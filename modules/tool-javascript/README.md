# JavaScript Tool Module

`:tool-javascript` contains the GraalJS-backed `javascript:execute` tool and its hardened
workspace/tool bridge. It depends on `:tool-standard` for the provider-neutral tool registry
and is exported transitively by the `:tools` aggregate.

The application supplies the `JavaScriptWorkspaceWriter` implementation. The module itself has
no application, UI, database, or direct host-filesystem dependency.
