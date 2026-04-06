import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/auth/reset-password')({
  component: AuthResetPasswordPage,
})

function AuthResetPasswordPage() {
  return <div className="p-4">Coming soon</div>
}
