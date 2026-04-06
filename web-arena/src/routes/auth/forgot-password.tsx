import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/auth/forgot-password')({
  component: AuthForgotPasswordPage,
})

function AuthForgotPasswordPage() {
  return <div className="p-4">Coming soon</div>
}
