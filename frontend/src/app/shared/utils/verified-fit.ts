
import { normalizeDeviceType } from './device-fit';
import { isDeviceTypeValue } from './device-classification';
import { CompatibleDevice } from '../../core/models/model';

/** An exact, model-specific fit recorded by a store manager. Never infer fit from a
 * product name, primary device classification, or a wildcard compatibility rule. */
export interface VerifiedFitItem { compatibleDevices: CompatibleDevice[] | null | undefined; }
export interface VerifiedFitQuery { deviceType: string; deviceBrand: string; deviceModel: string; }

function clean(value: string | null | undefined): string {
  return (value ?? '').trim().replace(/\s+/g, ' ').toLocaleLowerCase();
}
function validRecord(record: CompatibleDevice): boolean {
  return isDeviceTypeValue(record.deviceType) && !!clean(record.deviceBrand) && !!clean(record.deviceModel)
    && record.deviceType.trim() !== '*' && record.deviceBrand.trim() !== '*' && record.deviceModel.trim() !== '*';
}
export function verifiedFitSelected(query: VerifiedFitQuery): boolean {
  return query.deviceType !== 'ALL' && query.deviceBrand !== 'ALL' && !!query.deviceModel.trim();
}
export function matchesVerifiedFit(item: VerifiedFitItem, query: VerifiedFitQuery): boolean {
  if (!verifiedFitSelected(query)) return false;
  return (item.compatibleDevices ?? []).some(r => validRecord(r)
    && normalizeDeviceType(r.deviceType) === normalizeDeviceType(query.deviceType)
    && clean(r.deviceBrand) === clean(query.deviceBrand)
    && clean(r.deviceModel) === clean(query.deviceModel));
}
function uniqueSorted(values: string[]): string[] {
  const map = new Map<string,string>();
  for (const value of values) {
    const key=clean(value);
    if (key && !map.has(key)) map.set(key,value.trim());
  }
  return [...map.values()].sort((a,b)=>a.localeCompare(b));
}
/** Suggestions come only from exact, explicitly recorded device compatibility. */
export function verifiedFitTypes(items: VerifiedFitItem[]): string[] {
  return uniqueSorted(items.flatMap(item => (item.compatibleDevices ?? [])
    .filter(validRecord).map(r=>r.deviceType)));
}
export function verifiedFitBrands(items: VerifiedFitItem[], type: string): string[] {
  if(type === 'ALL')return [];
  return uniqueSorted(items.flatMap(item=>(item.compatibleDevices ?? [])
    .filter(r=>validRecord(r) && normalizeDeviceType(r.deviceType) === normalizeDeviceType(type))
    .map(r=>r.deviceBrand)));
}
export function verifiedFitModels(items: VerifiedFitItem[], type: string, brand: string): string[] {
  if(type === 'ALL' || brand === 'ALL')return [];
  return uniqueSorted(items.flatMap(item=>(item.compatibleDevices ?? [])
    .filter(r=>validRecord(r) && normalizeDeviceType(r.deviceType) === normalizeDeviceType(type)
      && clean(r.deviceBrand)===clean(brand))
    .map(r=>r.deviceModel)));
}
