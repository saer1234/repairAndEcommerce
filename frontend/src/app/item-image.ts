import { environment } from './environment';
/** Backend only returns server-generated relative image URLs; no external URLs are accepted. */
export function itemImageSrc(path: string | null | undefined, version?: number): string | null {
  if (!path || !/^\/api\/companies\/\d+\/items\/\d+\/image$/.test(path)) return null;
  const root = environment.apiUrl.replace(/\/api\/?$/, '');
  return `${root}${path}${version ? '?v=' + version : ''}`;
}
