import { z } from 'zod';

// Client-side schemas mirror the backend Bean Validation (server stays the
// authority). Messages match what the API returns so the UX is consistent
// whether validation trips client- or server-side.
const email = z
  .string()
  .min(1, 'Email is required')
  .email('Enter a valid email address');

const password = z
  .string()
  .min(12, 'Password must be between 12 and 128 characters')
  .max(128, 'Password must be between 12 and 128 characters');

export const signupSchema = z.object({ email, password });
export type SignupValues = z.infer<typeof signupSchema>;

export const loginSchema = z.object({
  email,
  password: z.string().min(1, 'Password is required'),
});
export type LoginValues = z.infer<typeof loginSchema>;

export const inviteSchema = z.object({ email });
export type InviteValues = z.infer<typeof inviteSchema>;

export const acceptInviteSchema = z.object({
  email,
  code: z.string().min(1, 'Enter the invite code'),
  password,
  name: z.string().min(1, 'Enter your name').max(100, 'Name must be at most 100 characters'),
});
export type AcceptInviteValues = z.infer<typeof acceptInviteSchema>;
