# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- web-arena: member self-service portal at port 5176 (phone OTP login, GX booking, PT sessions view, invoices with ZATCA QR, profile with language preference)
- ClubPortalSettings: per-club feature flags (gxBookingEnabled, ptViewEnabled, invoiceViewEnabled, onlinePaymentEnabled)
- Member phone OTP authentication: SHA-256 hashed codes, 10-minute TTL, rate limiting (max 3 per 10 minutes), no phone existence leak
- Member.preferredLanguage field (ar/en, nullable — drives language selection screen on first login)
- portal-settings:update permission added to Owner and Branch Manager roles
- web-pulse: portal settings management screen for club staff
