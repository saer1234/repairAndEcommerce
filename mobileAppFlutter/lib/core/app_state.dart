import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'api.dart';

class AppState extends ChangeNotifier {
  static const _secure = FlutterSecureStorage();
  static const _sessionKey = 'wolftech_mobile_session_v1';
  late final Api api = Api(token: () => token);
  String language = 'en';
  String token = '';
  DateTime? expiresAt;
  Map<String, dynamic>? user;
  List<Map<String, dynamic>> publicCompanies = [];
  List<Map<String, dynamic>> managedCompanies = [];
  int? companyId;
  int? managedCompanyId;
  bool initialized = false;
  String? startupError;

  bool get signedIn => token.isNotEmpty && (expiresAt?.isAfter(DateTime.now()) ?? false);
  String get role => str(user?['platformRole']);
  bool get isClient => signedIn && role == 'CLIENT' && managedCompanies.isEmpty;
  bool get isPlatformAdmin => signedIn && role == 'SUPER_ADMIN';
  bool get isStaff => signedIn && (managedCompanies.isNotEmpty || role == 'OWNER' || isPlatformAdmin);
  int get userId => numId(user?['id']);
  String get name => str(user?['fullName']);
  String get companyName {
    for (final company in publicCompanies) {
      if (numId(company['id']) == companyId) return str(company['name']);
    }
    return 'Wolf Tech Repair';
  }

  Future<void> init() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      language = prefs.getString('wolftech_lang') == 'ar' ? 'ar' : 'en';
      final stored = await _secure.read(key: _sessionKey);
      if (stored != null) {
        final session = object(jsonDecode(stored));
        final date = DateTime.tryParse(str(session['expiresAt']));
        if (date != null && date.isAfter(DateTime.now())) {
          expiresAt = date;
          token = str(session['accessToken']);
          user = object(session['user']);
        } else {
          await _secure.delete(key: _sessionKey);
        }
      }
      final savedCompany = prefs.getInt('wolftech_company');
      companyId = savedCompany;
      await refreshCompanies();
    } catch (e) {
      startupError = '$e';
    } finally {
      initialized = true;
      notifyListeners();
    }
  }

  Future<void> refreshCompanies() async {
    try {
      publicCompanies = objects(await api.get('/catalog/companies'));
      if (publicCompanies.isNotEmpty && !publicCompanies.any((e) => numId(e['id']) == companyId)) {
        // On the first launch, prefer the Wolf Tech Repair store when it is
        // published in the platform catalog. Preserve existing user selections.
        final branded = publicCompanies.where((company) =>
          str(company['name']).toLowerCase().replaceAll(RegExp(r'[^a-z0-9]'), '')
            .contains('wolftech'));
        companyId = numId((branded.isNotEmpty ? branded.first : publicCompanies.first)['id']);
      }
      if (signedIn) {
        try {
          managedCompanies = objects(await api.get('/companies'));
        } on ApiException catch (e) {
          if (e.status == 401) await logout();
          managedCompanies = [];
        }
      } else {
        managedCompanies = [];
      }
      if (managedCompanies.isNotEmpty &&
          !managedCompanies.any((e) => numId(e['id']) == managedCompanyId)) {
        managedCompanyId = numId(managedCompanies.first['id']);
      }
      startupError = null;
    } catch (e) {
      startupError = '$e';
    }
    notifyListeners();
  }

  Future<void> login(String email, String password) async {
    final result = object(await api.post('/auth/login', {'email': email.trim(), 'password': password}));
    final until = DateTime.tryParse(str(result['expiresAt']));
    if (until == null || !until.isAfter(DateTime.now())) {
      throw ApiException(401, 'Server returned an invalid or expired session');
    }
    token = str(result['accessToken']);
    expiresAt = until;
    user = object(result['user']);
    await _secure.write(key: _sessionKey, value: jsonEncode({
      'accessToken': token, 'expiresAt': result['expiresAt'], 'user': user,
    }));
    await refreshCompanies();
    notifyListeners();
  }

  Future<void> register({required String fullName, required String email,
      required String password, required String phone, required String accountType}) async {
    await api.post('/auth/register', {
      'fullName': fullName.trim(), 'email': email.trim(), 'password': password,
      'phone': phone.trim(), 'accountType': accountType,
    });
  }

  Future<void> logout() async {
    token = '';
    user = null;
    expiresAt = null;
    managedCompanies = [];
    managedCompanyId = null;
    await _secure.delete(key: _sessionKey);
    notifyListeners();
  }

  Future<void> setLanguage(String value) async {
    language = value == 'ar' ? 'ar' : 'en';
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('wolftech_lang', language);
    notifyListeners();
  }

  Future<void> selectCompany(int id) async {
    companyId = id;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('wolftech_company', id);
    notifyListeners();
  }
  void selectManagedCompany(int id) {
    managedCompanyId = id;
    notifyListeners();
  }
}
