import 'dart:convert';
import 'package:http/http.dart' as http;

/// Override at build time: --dart-define=API_BASE_URL=https://your-domain/api
const defaultApiBase = String.fromEnvironment(
  'API_BASE_URL', defaultValue: 'https://wolftech.space/api',
);

class ApiException implements Exception {
  final int status;
  final String message;
  ApiException(this.status, this.message);
  @override
  String toString() => message;
}

class Api {
  Api({required String Function() token, String base = defaultApiBase})
      : _token = token,
        base = base.replaceFirst(RegExp(r'/+$'), '');
  final String Function() _token;
  final String base;
  final http.Client _client = http.Client();

  Uri url(String path) => Uri.parse('$base${path.startsWith('/') ? path : '/$path'}');

  Uri? imageUri(dynamic path) {
    if (path is! String ||
        !RegExp(r'^/api/companies/\d+/items/\d+/image$').hasMatch(path)) {
      return null;
    }
    final origin = Uri.parse(base).origin;
    return Uri.parse('$origin$path');
  }

  Future<dynamic> uploadImage(String path, String localFile) async {
    final request = http.MultipartRequest('POST', url(path));
    final bearer = _token();
    if (bearer.isNotEmpty) request.headers['Authorization'] = 'Bearer $bearer';
    request.files.add(await http.MultipartFile.fromPath('file', localFile));
    http.Response response;
    try {
      final stream = await _client.send(request).timeout(const Duration(seconds: 40));
      response = await http.Response.fromStream(stream);
    } catch (error) {
      throw ApiException(0, 'Image upload failed: $error');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      String message = 'Upload rejected (${response.statusCode})';
      try {
        final obj = object(jsonDecode(response.body));
        message = str(obj['message'], message);
      } catch (_) { /* Binary/error fallback */ }
      throw ApiException(response.statusCode, message);
    }
    return response.body.isNotEmpty ? jsonDecode(response.body) : null;
  }

  Future<dynamic> get(String path) => _request('GET', path);
  Future<dynamic> post(String path, [Object? body]) => _request('POST', path, body);
  Future<dynamic> put(String path, [Object? body]) => _request('PUT', path, body);
  Future<dynamic> patch(String path, [Object? body]) => _request('PATCH', path, body);
  Future<dynamic> delete(String path) => _request('DELETE', path);

  Future<dynamic> _request(String method, String path, [Object? body]) async {
    final headers = <String, String>{'Accept': 'application/json'};
    final token = _token();
    if (token.isNotEmpty) headers['Authorization'] = 'Bearer $token';
    if (body != null) headers['Content-Type'] = 'application/json';
    http.Response response;
    try {
      final request = http.Request(method, url(path));
      request.headers.addAll(headers);
      if (body != null) request.body = jsonEncode(body);
      final streamed = await _client.send(request).timeout(const Duration(seconds: 25));
      response = await http.Response.fromStream(streamed);
    } catch (error) {
      throw ApiException(0, 'Connection failed: $error');
    }
    dynamic result;
    try {
      result = response.body.isEmpty ? null : jsonDecode(utf8.decode(response.bodyBytes));
    } catch (_) {
      result = response.body;
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      final map = result is Map ? result : const {};
      final reason = map['detail'] ?? map['message'] ?? map['error'];
      throw ApiException(response.statusCode,
          reason is String && reason.isNotEmpty ? reason : 'Request failed (${response.statusCode})');
    }
    return result;
  }

  void close() => _client.close();
}

Map<String, dynamic> object(dynamic value) =>
    value is Map ? Map<String, dynamic>.from(value) : <String, dynamic>{};
List<Map<String, dynamic>> objects(dynamic value) =>
    value is List ? value.map(object).toList() : <Map<String, dynamic>>[];
String str(dynamic value, [String fallback = '']) => value?.toString() ?? fallback;
int numId(dynamic value) => value is num ? value.toInt() : int.tryParse(str(value)) ?? 0;
double decimal(dynamic value) => value is num ? value.toDouble() : double.tryParse(str(value)) ?? 0;
