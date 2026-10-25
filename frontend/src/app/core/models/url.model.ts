export interface ShortenUrlRequest {
  longUrl: string;
  customAlias?: string;
  expirationDays?: number;
  tags?: string[];
}
export interface UrlResponse {
  shortCode: string;
  shortUrl: string;
  longUrl: string;
  customAlias?: string;
  isActive: boolean;
  expiresAt?: string;
  clickCount: number;
  tags: string[];
  createdAt: string;
}
export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp?: string;
}
