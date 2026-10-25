export interface AnalyticsSummary {
  shortCode: string;
  totalClicks: number;
  uniqueCountries: number;
  clicksByDate: Record<string, number>;
  clicksByCountry: Record<string, number>;
  clicksByDevice: Record<string, number>;
  clicksByBrowser: Record<string, number>;
}
export interface ClickEvent {
  id: number;
  shortCode: string;
  clickedAt: string;
  ipAddress?: string;
  country?: string;
  city?: string;
  deviceType?: string;
  browser?: string;
  os?: string;
  referrer?: string;
}
