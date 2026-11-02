import { CompatibleDevice } from './models';

export type Fit = 'compatible' | 'unspecified' | 'incompatible';
export interface FitItem { compatibleDevices: CompatibleDevice[]; }
export interface FitDevice { deviceType: string; brand: string | null; model: string | null; }
export function normalizeDeviceType(value: string | null | undefined): string {
  const s = (value || '').trim().toLocaleLowerCase();
  if (['phone', 'mobile', 'mobile phone', 'smart phone', 'smartphone', 'cellphone'].includes(s)) return 'phone';
  if (['notebook', 'laptop', 'laptop computer'].includes(s)) return 'laptop';
  if (['desktop', 'pc', 'computer'].includes(s)) return 'computer';
  return s;
}
function matches(expected: string, actual: string | null | undefined) {
  return expected === '*' || (actual != null && expected.trim().toLowerCase() === actual.trim().toLowerCase());
}
export function compatibilityStatus(item: FitItem, device: FitDevice): Fit {
  const options = item.compatibleDevices || [];
  if (!options.length) return 'unspecified';
  return options.some(c =>
    (c.deviceType === '*' || normalizeDeviceType(c.deviceType) === normalizeDeviceType(device.deviceType))
    && matches(c.deviceBrand, device.brand) && matches(c.deviceModel, device.model)
  ) ? 'compatible' : 'incompatible';
}
