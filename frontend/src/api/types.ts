export type Role = 'MANAGER' | 'ASSISTANT';
export type PaymentMethod = 'CASH' | 'CARD' | 'MOBILE';
export type ProductStatus = 'EXPIRED' | 'OUT' | 'LOW' | 'EXPIRING' | 'OK';
export type MovementType = 'INITIAL' | 'RECEIVE' | 'SALE' | 'VOID_RETURN' | 'WASTAGE' | 'CORRECTION';
export type PoStatus = 'DRAFT' | 'ORDERED' | 'RECEIVED' | 'CANCELLED';

export interface UserInfo { id: number; username: string; fullName: string; email: string | null; role: Role; mustChangePassword: boolean }
export interface TokenPair { accessToken: string; refreshToken: string; tokenType: string; expiresInSeconds: number; user: UserInfo }

export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }

export interface Category { id: number; name: string }
export interface Supplier { id: number; name: string; phone: string | null; email: string | null; address: string | null }

export interface Product {
  id: number; name: string; category: string; supplier: string | null; price: number; cost: number; marginPercent: number;
  quantity: number; reorderLevel: number; expiryDate: string | null; daysToExpiry: number | null; status: ProductStatus; active: boolean;
}
export interface ProductInput {
  name: string; category: string; supplier?: string; price: number; cost: number; quantity?: number; reorderLevel?: number; expiryDate?: string | null;
}
export interface Movement { id: number; productId: number; productName: string; type: MovementType; change: number; balanceAfter: number; user: string; note: string; createdAt: string }

export interface SaleItem { productId: number; name: string; category: string; unitPrice: number; quantity: number; lineTotal: number }
export interface Sale {
  id: number; receiptNo: string; createdAt: string; cashier: string; paymentMethod: PaymentMethod; subtotal: number; discountPercent: number;
  discountAmount: number; taxRate: number; taxAmount: number; total: number; tendered: number; change: number; units: number;
  voided: boolean; voidedBy: string | null; voidedAt: string | null; voidReason: string | null; items: SaleItem[];
}

export interface PoItem { productId: number; name: string; quantityOrdered: number; quantityReceived: number; unitCost: number; lineTotal: number }
export interface PurchaseOrder {
  id: number; poNumber: string; supplierId: number | null; supplier: string | null; status: PoStatus; notes: string | null; createdBy: string;
  createdAt: string; orderedAt: string | null; receivedAt: string | null; total: number; items: PoItem[];
}

export interface Bucket { label: string; revenue: number; profit: number; units: number }
export interface Summary {
  from: string; to: string; transactions: number; voidedTransactions: number; units: number; grossSales: number; discounts: number; tax: number;
  netRevenue: number; costOfGoods: number; profit: number; averageBasket: number;
  byPayment: Bucket[]; byCategory: Bucket[]; byCashier: Bucket[]; topProducts: Bucket[]; byHour: Bucket[]; byWeekday: Bucket[]; byDay: Bucket[];
}
export interface Dashboard {
  today: Summary; yesterday: Summary; dailyTarget: number; targetProgressPercent: number; productCount: number; lowStock: number; outOfStock: number;
  expiringSoon: number; expired: number; stockValueAtCost: number; stockValueAtRetail: number; last7Days: Bucket[];
}
export interface ReorderSuggestion { productId: number; name: string; supplier: string; quantity: number; reorderLevel: number; suggestedOrder: number; unitCost: number; estimatedCost: number }
export interface DeadStock { productId: number; name: string; category: string; quantity: number; valueAtCost: number; lastSold: string | null }

export interface StaffUser {
  id: number; username: string; fullName: string; email: string | null; role: Role; active: boolean; locked: boolean;
  mustChangePassword: boolean; lastLogin: string | null; createdAt: string;
}
export interface Activity { id: number; username: string; action: string; details: string; createdAt: string }
export type Settings = Record<string, string>;
