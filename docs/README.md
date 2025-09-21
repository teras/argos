# Argos Documentation

Documentation for the Argos command-line argument parsing library, built with [MkDocs](https://www.mkdocs.org/) and [Material theme](https://squidfunk.github.io/mkdocs-material/).

## Quick Start

```bash
./build.sh install    # Install dependencies
./build.sh serve      # Start development server
```

Open http://localhost:8000 in your browser.

## Build Commands

| Command | Description |
|---------|-------------|
| `./build.sh install` | Install dependencies in virtual environment |
| `./build.sh serve` | Start development server |
| `./build.sh build` | Build static site |
| `./build.sh deploy` | Deploy to GitHub Pages |
| `./build.sh validate` | Validate documentation |
| `./build.sh clean` | Clean build artifacts |
| `./build.sh help` | Show all commands |

## Requirements

- Python 3.8+ with `venv`
- Git (for deployment)
- Bash shell

## Troubleshooting

**Setup issues:**
```bash
chmod +x build.sh           # Make script executable
rm -rf venv/                # Reset virtual environment
./build.sh install          # Reinstall dependencies
```

**Build issues:**
```bash
./build.sh clean            # Clean artifacts
./build.sh validate         # Check for errors
./build.sh check-links      # Validate links
```

## Documentation Structure

The documentation source files are in `src/`:

- `getting-started/` - Installation and tutorials
- `guide/` - User guides
- `api/` - API reference
- `examples/` - Complete examples
- `advanced/` - Advanced topics
- `contributing/` - Development guides

For detailed documentation guidelines and contribution instructions, see the files in `src/contributing/`.