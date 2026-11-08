export interface CompanyAbout {
  companyId: number; companyName: string; headline: string; description: string | null;
  storeAddress: string | null; contactPhone: string | null; contactEmail: string | null;
  mapsUrl: string | null; instagramUrl: string | null; facebookUrl: string | null;
  tiktokUrl: string | null; whatsappUrl: string | null; websiteUrl: string | null;
  openingHours: string | null;
}
export type CompanyAboutEdit = Omit<CompanyAbout, 'companyId' | 'companyName'>;
