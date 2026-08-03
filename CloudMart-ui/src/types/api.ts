export interface ApiResponse<T = unknown> {
  success: boolean;
  data: T;
  error?: {
    code: string;
    message: string;
    details?: unknown[];
  };
  meta?: {
    page: number;
    pageSize: number;
    total: number;
  };
}
