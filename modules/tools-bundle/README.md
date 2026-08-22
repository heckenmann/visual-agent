# Tool Bundle

`:tools` is the complete model-tool aggregator. Consumers bind this module to receive the
standard tools and the sandboxed JavaScript tool transitively.

The aggregate contains no tool implementation of its own. Application services are still
provided through ports and composed by `:application`.
