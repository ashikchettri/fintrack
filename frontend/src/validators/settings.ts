import { z } from 'zod';

const strongPassword = z
  .string()
  .min(12, 'Password must be between 12 and 128 characters')
  .max(128, 'Password must be between 12 and 128 characters');

export const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, 'Current password is required'),
  newPassword: strongPassword,
});
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>;

export const changeEmailSchema = z.object({
  newEmail: z.string().min(1, 'Email is required').email('Enter a valid email address'),
  currentPassword: z.string().min(1, 'Current password is required'),
});
export type ChangeEmailValues = z.infer<typeof changeEmailSchema>;

export const confirmEmailSchema = z.object({
  code: z.string().regex(/^\d{6}$/, 'Enter the 6-digit code'),
});
export type ConfirmEmailValues = z.infer<typeof confirmEmailSchema>;
