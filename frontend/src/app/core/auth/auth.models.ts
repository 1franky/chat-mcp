export type UserRole = 'ADMIN' | 'USER';

export interface UserAccount {
  id: string;
  email: string;
  displayName: string;
  role: UserRole;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AuthBootstrap {
  bootstrapRequired: boolean;
  registrationOpen: boolean;
  authenticated: boolean;
  user: UserAccount | null;
}

export interface RegisterRequest {
  email: string;
  displayName: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface UserPage {
  users: UserAccount[];
  totalElements: number;
  page: number;
  size: number;
}
