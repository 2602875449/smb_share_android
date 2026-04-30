# Backend Development Guidelines

> Project-specific guidelines for the Android app's data, domain, service, persistence, and non-UI business logic layers.

---

## Overview

This repository does not contain a server backend. The files in this directory document the existing Android app patterns that future agents should follow when changing data, domain, service, persistence, and related UI-facing logic.

---

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | Android module organization and layer boundaries | Filled |
| [Database Guidelines](./database-guidelines.md) | Room, DataStore, serialization, and persistence tests | Filled |
| [Error Handling](./error-handling.md) | App error categories, Result propagation, UI display | Filled |
| [Quality Guidelines](./quality-guidelines.md) | Kotlin, Compose, theme, localization, and testing patterns | Filled |
| [Logging Guidelines](./logging-guidelines.md) | Android Log usage, tags, levels, and sensitive data boundaries | Filled |

---

## Pre-Development Checklist

Before coding in this repository, read the relevant files below:

- For structure, state ownership, or package placement: [Directory Structure](./directory-structure.md)
- For Room, DataStore, entity conversion, or serialization: [Database Guidelines](./database-guidelines.md)
- For exception mapping, Result propagation, Snackbar errors, or transfer retries: [Error Handling](./error-handling.md)
- For logs, tags, levels, or sensitive data: [Logging Guidelines](./logging-guidelines.md)
- For Compose state, theme colors, localization, tests, or verification commands: [Quality Guidelines](./quality-guidelines.md)

These guidelines intentionally describe current code examples only. Do not treat them as permission to refactor app architecture or add unsupported frameworks.

---

**Language**: All documentation should be written in **English**.
