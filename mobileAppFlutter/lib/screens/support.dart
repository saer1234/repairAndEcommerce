import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';
import '../core/api.dart';
import '../core/i18n.dart';
import '../widgets/ui.dart';

/// V26-compatible WebSocket support. A staff socket is bound to its presence
/// lease so server-side afterConnectionClosed can clear availability promptly.
class SupportScreen extends StatefulWidget {
  const SupportScreen({super.key, required this.staff});
  final bool staff;
  @override
  State<SupportScreen> createState() => _SupportScreenState();
}

class _SupportScreenState extends State<SupportScreen> with WidgetsBindingObserver {
  final sessionId = const Uuid().v4();
  final draft = TextEditingController();
  final offlineDraft = TextEditingController();
  final offlineReply = TextEditingController();
  final scroller = ScrollController();
  Api? api;
  int? companyId;
  WebSocket? socket;
  Timer? reconnectTimer;
  Timer? heartbeatTimer;
  int retry = 0;
  int generation = 0;
  bool active = true;
  bool foreground = true;
  bool connected = false;
  bool available = false;
  bool shopOnline = false;
  int agents = 0;
  bool busy = false;
  String error = '';
  List<Map<String, dynamic>> conversations = [];
  Map<String, dynamic>? selected;
  List<Map<String, dynamic>> messages = [];
  List<Map<String, dynamic>> offlineMessages = [];
  Map<int, bool> customerOnline = {};
  bool showOffline = false;
  Map<String, dynamic>? selectedOffline;

  bool get canSend => connected && selected != null && str(selected!['status']) == 'OPEN' &&
      shopOnline && (!widget.staff || available) &&
      (!widget.staff || customerOnline[numId(selected!['customerId'])] != false) && !busy;
  String get base => '/companies/$companyId/support';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    heartbeatTimer = Timer.periodic(const Duration(seconds: 25), (_) {
      if (widget.staff && available && connected && foreground) heartbeat();
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final state = AppScope.of(context);
    api = state.api;
    final next = widget.staff ? state.managedCompanyId : state.companyId;
    if (next != companyId) {
      _disconnect();
      companyId = next;
      selected = null;
      conversations = [];
      messages = [];
      offlineMessages = [];
      customerOnline = {};
      available = false;
      if (next != null) Future.microtask(_enter);
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused || state == AppLifecycleState.hidden) {
      foreground = false;
      if (widget.staff && available) _leavePresence();
      _disconnect();
    } else if (state == AppLifecycleState.resumed) {
      foreground = true;
      Future.microtask(_enter);
    }
  }

  Future<void> _enter() async {
    if (!active || !foreground || companyId == null || api == null) return;
    await _connect();
  }

  Future<void> _connect() async {
    if (!active || !foreground || companyId == null || api == null || socket != null) return;
    final version = ++generation;
    try {
      final credential = object(await api!.post('$base/ws-ticket', {}));
      if (!active || !foreground || version != generation) return;
      final root = api!.url('/support/ws');
      final uri = root.replace(scheme: root.scheme == 'https' ? 'wss' : 'ws',
        queryParameters: {'ticket': str(credential['ticket'])});
      final ws = await WebSocket.connect(uri.toString()).timeout(const Duration(seconds: 12));
      if (!active || !foreground || version != generation) { await ws.close(); return; }
      ws.pingInterval = const Duration(seconds: 20);
      socket = ws;
      retry = 0;
      if (widget.staff) {
        ws.add(jsonEncode({'type': 'staff_presence_bind', 'sessionId': sessionId}));
        if (available) await heartbeat();
      }
      if (mounted) setState(() => connected = true);
      ws.listen((raw) {
        if (!active || version != generation) return;
        try { _frame(object(jsonDecode(raw.toString()))); } catch (_) { /* Ignore invalid frames */ }
      }, onDone: () => _socketClosed(version), onError: (_) => _socketClosed(version), cancelOnError: true);
      await refresh();
    } catch (e) {
      if (!active || version != generation) return;
      if (mounted) setState(() { connected = false; error = '$e'; });
      _scheduleReconnect();
    }
  }

  void _frame(Map<String, dynamic> event) {
    final type = str(event['type']);
    switch (type) {
      case 'refresh':
        refresh();
        break;
      case 'staff_availability':
        if (mounted) setState(() {
          shopOnline = event['online'] == true;
          agents = numId(event['availableAgents']);
        });
        break;
      case 'customer_presence':
        if (widget.staff && mounted) setState(() =>
          customerOnline[numId(event['customerId'])] = event['online'] == true);
        break;
      case 'message':
      case 'ack':
        final msg = object(event['message']);
        if (selected != null && numId(msg['conversationId']) == numId(selected!['id'])) {
          if (mounted) setState(() {
            if (!messages.any((m) => numId(m['id']) == numId(msg['id']))) messages.add(msg);
          });
          _scrollToEnd();
        } else { refresh(); }
        break;
      case 'error':
        if (mounted) setState(() { error = str(event['message'], 'Chat error'); busy = false; });
        break;
    }
  }

  void _socketClosed(int version) {
    if (version != generation) return;
    socket = null;
    if (mounted) setState(() {
      connected = false;
      busy = false;
      if (widget.staff) available = false; // Bound server lease has ended.
    });
    _scheduleReconnect();
  }

  void _scheduleReconnect() {
    if (!active || !foreground || companyId == null) return;
    reconnectTimer?.cancel();
    final seconds = (1 << retry.clamp(0, 4).toInt());
    retry++;
    reconnectTimer = Timer(Duration(seconds: seconds), () { if (active) _connect(); });
  }

  void _disconnect() {
    generation++;
    reconnectTimer?.cancel();
    reconnectTimer = null;
    final previous = socket;
    socket = null;
    connected = false;
    if (previous != null) previous.close(WebSocketStatus.normalClosure);
  }

  Future<void> _leavePresence() async {
    if (!widget.staff || companyId == null || api == null) return;
    available = false;
    try { await api!.delete('$base/presence/$sessionId'); } catch (_) { /* Socket close is server fallback. */ }
  }

  Future<void> heartbeat() async {
    if (!widget.staff || companyId == null || api == null || !connected) return;
    try {
      final result = object(await api!.post('$base/presence/heartbeat', {'sessionId': sessionId}));
      if (!mounted) return;
      setState(() { available = true; shopOnline = result['online'] == true; agents = numId(result['availableAgents']); error = ''; });
    } catch (e) { if (mounted) setState(() { available = false; error = '$e'; }); }
  }

  Future<void> toggleAvailability() async {
    if (!widget.staff || companyId == null) return;
    if (available) {
      await _leavePresence();
      if (mounted) setState(() => available = false);
    } else { await heartbeat(); }
    if (mounted) await refresh();
  }

  Future<void> refresh() async {
    if (!mounted || companyId == null || api == null) return;
    final id = companyId;
    try {
      final availability = object(await api!.get('$base/availability'));
      if (!mounted || companyId != id) return;
      setState(() { shopOnline = availability['online'] == true; agents = numId(availability['availableAgents']); });
      final offline = objects(await api!.get('$base/${widget.staff ? 'offline-messages' : 'offline-messages/mine'}'));
      if (!mounted || companyId != id) return;
      setState(() { offlineMessages = offline; selectedOffline ??= offline.isNotEmpty ? offline.first : null; });
      if (widget.staff) {
        final incoming = objects(await api!.get('$base/conversations'));
        if (!mounted || companyId != id) return;
        final oldId = numId(selected?['id']);
        final found = incoming.where((c) => numId(c['id']) == oldId);
        setState(() {
          conversations = incoming;
          selected = found.isNotEmpty ? found.first : incoming.isNotEmpty ? incoming.first : null;
        });
      } else {
        final current = await api!.get('$base/mine');
        if (!mounted || companyId != id) return;
        setState(() => selected = current == null ? null : object(current));
      }
      await loadMessages();
    } catch (e) { if (mounted) setState(() => error = '$e'); }
  }

  Future<void> loadMessages() async {
    final conversation = selected;
    if (conversation == null || companyId == null || api == null) {
      if (mounted) setState(() => messages = []);
      return;
    }
    final conversationId = numId(conversation['id']);
    try {
      final fetched = objects(await api!.get('$base/conversations/$conversationId/messages'));
      if (!mounted || numId(selected?['id']) != conversationId) return;
      final byId = {for (final msg in fetched) numId(msg['id']): msg};
      for (final m in messages.where((m) => numId(m['conversationId']) == conversationId)) {
        byId[numId(m['id'])] = m;
      }
      if (mounted) setState(() => messages = byId.values.toList()
        ..sort((a, b) => numId(a['id']).compareTo(numId(b['id']))));
      _scrollToEnd();
    } catch (e) { if (mounted) setState(() => error = '$e'); }
  }

  void _scrollToEnd() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (scroller.hasClients) scroller.jumpTo(scroller.position.maxScrollExtent);
    });
  }

  Future<void> start() async {
    if (!connected || !shopOnline || widget.staff) return;
    setState(() { busy = true; error = ''; });
    try {
      final result = object(await api!.post('$base/conversations', {}));
      if (mounted) setState(() => selected = result);
      await loadMessages();
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }

  void send() {
    if (!canSend || draft.text.trim().isEmpty || draft.text.trim().length > 2000) return;
    final text = draft.text.trim();
    try {
      final requestId = const Uuid().v4();
      socket!.add(jsonEncode({
        'type': 'send', 'requestId': requestId, 'conversationId': numId(selected!['id']), 'body': text,
      }));
      setState(() { draft.clear(); error = ''; });
    } catch (e) { if (mounted) setState(() => error = '$e'); }
  }

  Future<void> sendOffline() async {
    if (offlineDraft.text.trim().isEmpty || api == null) return;
    setState(() { busy = true; error = ''; });
    try {
      await api!.post('$base/offline-messages', {'body': offlineDraft.text.trim()});
      offlineDraft.clear();
      await refresh();
      if (mounted) toast(context, 'Send inquiry');
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }

  Future<void> replyOffline() async {
    if (selectedOffline == null || offlineReply.text.trim().isEmpty || api == null) return;
    setState(() => busy = true);
    try {
      await api!.patch('$base/offline-messages/${selectedOffline!['id']}/reply', {'body': offlineReply.text.trim()});
      offlineReply.clear(); selectedOffline = null;
      await refresh();
    } catch (e) { if (mounted) setState(() => error = '$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }

  Future<void> closeConversation() async {
    if (!widget.staff || selected == null) return;
    try {
      await api!.patch('$base/conversations/${selected!['id']}/status', {'status': 'CLOSED'});
      await refresh();
    } catch (e) { if (mounted) setState(() => error = '$e'); }
  }

  @override
  void dispose() {
    active = false;
    WidgetsBinding.instance.removeObserver(this);
    heartbeatTimer?.cancel();
    if (widget.staff && available) _leavePresence();
    _disconnect();
    draft.dispose(); offlineDraft.dispose(); offlineReply.dispose(); scroller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = AppScope.of(context);
    final id = widget.staff ? state.managedCompanyId : state.companyId;
    final online = widget.staff ? available : shopOnline;
    final canStart = !widget.staff && connected && shopOnline;
    final showEnded = selected != null &&
      (str(selected!['status']) != 'OPEN' || !shopOnline || !connected ||
        (widget.staff && customerOnline[numId(selected!['customerId'])] == false));
    return Column(children: [
      Padding(padding: const EdgeInsets.fromLTRB(15, 10, 15, 7), child: Column(children: [
        Row(children: [
          const Icon(Icons.forum_outlined, color: brandBlue), const SizedBox(width: 8),
          Expanded(child: Text(tr(context, 'Live chat'), style: Theme.of(context).textTheme.titleLarge)),
          Chip(avatar: Icon(connected ? Icons.circle : Icons.circle_outlined,
            size: 12, color: connected ? Colors.green : Colors.red),
            label: Text(tr(context, connected ? 'Connected' : 'Not connected'))),
        ]),
        if (widget.staff) ...[
          const SizedBox(height: 8),
          SizedBox(width: double.infinity, child: ActionButton(
            text: available ? 'Go offline' : 'Available for chat',
            icon: available ? Icons.do_not_disturb_on_outlined : Icons.support_agent_outlined,
            onPressed: connected ? toggleAvailability : null)),
        ] else Padding(padding: const EdgeInsets.only(top: 4),
          child: Text('${tr(context, online ? 'Connected' : 'Staff offline')} · $agents agents',
            style: const TextStyle(color: Colors.blueGrey))),
        if (error.isNotEmpty) Notice(error, error: true),
        if (id == null) const EmptyState(text: 'Select company'),
        if (widget.staff) SegmentedButton<bool>(segments: [
          ButtonSegment(value: false, label: Text(tr(context, 'Live chat'))),
          ButtonSegment(value: true, label: Text(tr(context, 'Offline inquiry'))),
        ], selected: {showOffline}, onSelectionChanged: (v) => setState(() => showOffline = v.first)),
        if (widget.staff && !showOffline && conversations.isNotEmpty) ...[
          const SizedBox(height: 6),
          SizedBox(height: 45, child: ListView(scrollDirection: Axis.horizontal, children: conversations.map((c) =>
            Padding(padding: const EdgeInsetsDirectional.only(end: 7),
              child: ChoiceChip(label: Text(str(c['customerName'])),
                selected: numId(selected?['id']) == numId(c['id']),
                onSelected: (_) { setState(() { selected = c; messages = []; }); loadMessages(); }))).toList())),
        ],
      ])),
      Expanded(child: showOffline && widget.staff ? _buildOffline() :
        selected == null ? ListView(children: [
          const SizedBox(height: 60),
          EmptyState(text: widget.staff ? 'No conversations yet' : 'Start live chat', icon: Icons.chat_bubble_outline),
          if (canStart) Center(child: ActionButton(text: 'Start live chat', onPressed: start)),
        ]) : Column(children: [
          Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 5), child: Row(children: [
            Expanded(child: Text(widget.staff ? str(selected!['customerName']) : str(selected!['companyName']),
              style: const TextStyle(fontWeight: FontWeight.bold))),
            if (widget.staff && str(selected!['status']) == 'OPEN')
              IconButton(onPressed: closeConversation, icon: const Icon(Icons.close)),
          ])),
          if (showEnded) Container(width: double.infinity, margin: const EdgeInsets.all(9),
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(color: const Color(0xFFFFEDE4), borderRadius: BorderRadius.circular(12)),
            child: Text(tr(context, widget.staff && customerOnline[numId(selected!['customerId'])] == false
                ? 'Customer offline' : 'Chat ended'), textAlign: TextAlign.center)),
          Expanded(child: ListView.builder(controller: scroller, padding: const EdgeInsets.all(14),
            itemCount: messages.length, itemBuilder: (context, i) {
              final m = messages[i];
              final mine = numId(m['senderId']) == state.userId;
              return Align(alignment: mine ? AlignmentDirectional.centerEnd : AlignmentDirectional.centerStart,
                child: Container(margin: const EdgeInsets.symmetric(vertical: 4),
                  padding: const EdgeInsets.all(12),
                  constraints: BoxConstraints(maxWidth: MediaQuery.sizeOf(context).width * .76),
                  decoration: BoxDecoration(color: mine ? brandBlue : const Color(0xFFE5EDF8),
                    borderRadius: BorderRadius.circular(16)),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(str(m['senderName']), style: TextStyle(fontSize: 11,
                      color: mine ? Colors.white70 : Colors.blueGrey)),
                    const SizedBox(height: 3),
                    Text(str(m['body']), style: TextStyle(color: mine ? Colors.white : brandNavy)),
                  ])));
            })),
          SafeArea(top: false, child: Padding(padding: const EdgeInsets.all(10), child: Row(children: [
            Expanded(child: TextField(controller: draft,
              enabled: canSend, maxLength: 2000, minLines: 1, maxLines: 3,
              decoration: fieldDecoration(context, 'Write a message').copyWith(counterText: ''))),
            const SizedBox(width: 7),
            IconButton.filled(onPressed: canSend ? send : null, icon: const Icon(Icons.send)),
          ]))),
        ])),
      if (!widget.staff && offlineMessages.isNotEmpty)
        ExpansionTile(title: Text(tr(context, 'Offline messages')),
          children: offlineMessages.map((inquiry) => ListTile(
            title: Text(str(inquiry['body'])),
            subtitle: str(inquiry['reply']).isNotEmpty
              ? Text('${tr(context, 'Reply')}: ${str(inquiry['reply'])}')
              : null,
          )).toList()),
      if (!widget.staff && !shopOnline) Padding(padding: const EdgeInsets.all(12), child: RoundedCard(
        child: Column(children: [
          Text(tr(context, 'Staff offline'), style: const TextStyle(fontWeight: FontWeight.bold)),
          TextField(controller: offlineDraft, maxLines: 2, maxLength: 2000,
            decoration: fieldDecoration(context, 'Offline inquiry')),
          ActionButton(text: 'Send inquiry', onPressed: sendOffline),
        ]))),
    ]);
  }

  Widget _buildOffline() => ListView(padding: const EdgeInsets.all(12), children: [
    if (offlineMessages.isEmpty) const EmptyState(text: 'No conversations yet'),
    for (final inquiry in offlineMessages) Padding(padding: const EdgeInsets.only(bottom: 10),
      child: RoundedCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(str(inquiry['customerName']), style: const TextStyle(fontWeight: FontWeight.bold)),
        Text(str(inquiry['body'])),
        if (str(inquiry['reply']).isNotEmpty)
          Text('${tr(context, 'Reply')}: ${str(inquiry['reply'])}',
            style: const TextStyle(color: brandBlue)),
        if (str(inquiry['reply']).isEmpty)
          TextButton.icon(icon: const Icon(Icons.reply), label: Text(tr(context, 'Reply')),
            onPressed: () => setState(() { selectedOffline = inquiry; offlineReply.clear(); })),
        if (numId(selectedOffline?['id']) == numId(inquiry['id']) && str(inquiry['reply']).isEmpty) ...[
          TextField(controller: offlineReply, maxLines: 2,
            decoration: fieldDecoration(context, 'Reply')),
          const SizedBox(height: 6),
          ActionButton(text: 'Send', onPressed: busy ? null : replyOffline),
        ],
      ]))),
  ]);
}
