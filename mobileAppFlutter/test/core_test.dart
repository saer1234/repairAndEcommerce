import 'package:flutter_test/flutter_test.dart';
import 'package:wolftech_mobile/core/api.dart';
import 'package:wolftech_mobile/core/i18n.dart';

void main() {
  test('API paths have the configured /api prefix', () {
    final api = Api(token: () => '', base: 'https://example.com/api');
    expect(api.url('/catalog/companies').toString(), 'https://example.com/api/catalog/companies');
    api.close();
  });

  test('image URLs are only accepted from the backend item path', () {
    final api = Api(token: () => '', base: 'https://example.com/api');
    expect(api.imageUri('/api/companies/10/items/5/image').toString(),
      'https://example.com/api/companies/10/items/5/image');
    expect(api.imageUri('https://evil.example/track'), isNull);
    expect(api.imageUri('/admin/secret'), isNull);
    api.close();
  });

  test('Arabic dictionary has important commerce and auth entries', () {
    for (final key in ['Store','Sign in','Create account','Add to cart','Checkout','Support','Repairs']) {
      expect(ar.containsKey(key), isTrue, reason: key);
    }
  });
}
