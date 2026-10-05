export type Role = 'ADMIN' | 'DOCTOR' | 'PATIENT';
export interface User {
  id: number;
  username: string;
  displayName: string;
  roles: Role[];
  doctorId?: number;
}
export interface R<T> { code: string; message: string; data: T; traceId?: string }
export interface PageResult<T> { records: T[]; total: number; page: number; size: number }
export type DataRow = Record<string, unknown> & { id: number };
export interface Department extends DataRow { name: string; status: number }
export interface Doctor extends DataRow { name: string; departmentId: number; departmentName: string; status: number }
export type ScheduleStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'CANCELLED';
export interface Schedule extends DataRow {
  doctorId: number; doctorName: string; departmentName: string;
  startTime: string; endTime: string; totalSlots: number; bookedSlots: number;
  remainingSlots: number; status: ScheduleStatus; cancelBeforeMinutes: number;
}
export type AppointmentStatus = 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'NO_SHOW' | 'CANCELLED';
export interface Appointment extends DataRow {
  appointmentNo: string; patientName: string; patientPhone: string;
  doctorName: string; departmentName: string; startTime: string; endTime: string;
  chiefComplaint?: string; status: AppointmentStatus; cancelReason?: string;
  cancelledAt?: string; createdAt: string;
}
export interface Dashboard {
  todayAppointments: number; pendingAppointments: number; totalPatients: number; totalDoctors: number;
  statusCounts: { status: AppointmentStatus; count: number }[];
  dailyTrend: { date: string; count: number }[];
}
export interface Clinic {
  name: string; phone: string; address: string; openingHours: string;
  introduction: string; cancelBeforeMinutes: number;
}
