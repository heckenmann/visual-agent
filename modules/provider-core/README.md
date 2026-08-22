# Provider Core

`:provider-core` contains provider contracts, profile/catalog models, provider settings ports,
and shared provider error handling. Concrete adapters depend on this module; the aggregate
`:providers` combines those adapters for application consumers.
