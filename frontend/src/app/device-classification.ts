/** Product category and device type are DIFFERENT concepts. */
const PART_CATEGORIES = new Set([
  'screen','display','battery','charging port','charging cable','charging flex',
  'speaker','microphone','back cover','rear cover','housing','motherboard',
  'flex cable','camera module','camera lens','charging connector',
  'power button','vibration motor','sim tray','antenna'
]);
const norm = (s: string | null | undefined) => (s ?? '').trim().toLocaleLowerCase();
export function isDeviceTypeValue(value: string | null | undefined): boolean {
  const v = norm(value);
  return !!v && v !== 'general' && v !== 'uncategorized' && v !== '*'
    && !PART_CATEGORIES.has(v);
}
/** Old data may have used device type in the component category field. */
export function componentCategory(item: {category?: string | null; deviceType?: string | null}): string {
  const value = (item.category ?? '').trim();
  return !value || (norm(value) === norm(item.deviceType) && isDeviceTypeValue(value)) ||
    ['phone','mobile','mobile phone','smartphone','tablet','laptop','desktop',
     'computer','game console','smartwatch','wearable'].includes(norm(value)) ? 'Uncategorized' : value;
}
export function componentCategories(items: Array<{category?: string | null; deviceType?: string | null}>): string[] {
  return [...new Set(items.map(componentCategory))].sort((a, b) => a.localeCompare(b));
}
