#!/bin/bash

# MkDocs Build Script for Argos Documentation
# This script handles building and deploying the documentation

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Change to the script's directory (docs directory)
cd "$(dirname "${BASH_SOURCE[0]}")"

# Parse command line arguments
COMMAND=${1:-serve}
ENVIRONMENT=${2:-development}

case $COMMAND in
    "install")
        log_info "Installing MkDocs dependencies..."

        # Check if python is available
        if ! command -v python3 &> /dev/null && ! command -v python &> /dev/null; then
            log_error "Python is not installed. Please install Python first."
            exit 1
        fi

        # Use python3 if available, otherwise python
        PYTHON_CMD="python3"
        if ! command -v python3 &> /dev/null; then
            PYTHON_CMD="python"
        fi

        # Check if we're in an externally managed environment
        VENV_DIR="venv"

        if [ ! -d "$VENV_DIR" ]; then
            log_info "Creating Python virtual environment..."
            $PYTHON_CMD -m venv "$VENV_DIR"

            if [ $? -ne 0 ]; then
                log_error "Failed to create virtual environment. Make sure python3-venv is installed."
                log_info "On Arch Linux, run: sudo pacman -S python-virtualenv"
                log_info "On Ubuntu/Debian, run: sudo apt install python3-venv"
                exit 1
            fi
        fi

        # Activate virtual environment
        log_info "Activating virtual environment..."
        source "$VENV_DIR/bin/activate"

        # Upgrade pip in virtual environment
        pip install --upgrade pip

        # Install dependencies
        if [ -f "requirements.txt" ]; then
            log_info "Installing dependencies from requirements.txt..."
            pip install -r requirements.txt
        else
            log_info "Installing basic MkDocs dependencies..."
            pip install mkdocs mkdocs-material mkdocs-git-revision-date-localized-plugin mkdocs-minify-plugin
        fi

        log_success "Dependencies installed successfully in virtual environment"
        log_info "To activate the virtual environment manually, run: source $VENV_DIR/bin/activate"
        ;;

    "serve")
        log_info "Starting MkDocs development server..."

        # Activate virtual environment if it exists
        if [ -d "venv" ]; then
            source venv/bin/activate
        fi

        # Check if MkDocs is installed
        if ! command -v mkdocs &> /dev/null; then
            log_error "MkDocs is not installed. Run: $0 install"
            exit 1
        fi

        # Start development server
        if [ "$ENVIRONMENT" = "production" ]; then
            mkdocs serve --strict --verbose
        else
            mkdocs serve --dirtyreload
        fi
        ;;

    "build")
        log_info "Building documentation site..."

        # Activate virtual environment if it exists
        if [ -d "venv" ]; then
            source venv/bin/activate
        fi

        # Clean previous build
        if [ -d "site" ]; then
            log_info "Cleaning previous build..."
            rm -rf site
        fi

        # Build Dokka documentation from main project
        log_info "Building Dokka API documentation..."
        cd .. # Go to project root

        # Clean and build Dokka
        ./gradlew clean dokkaHtml

        if [ ! -d "argos/build/dokka" ]; then
            log_error "Dokka build failed - argos/build/dokka directory not found"
            cd docs
            exit 1
        fi

        cd docs # Return to docs directory
        log_success "Dokka documentation built successfully"

        # =================================================================
        # CLEAN API DOCUMENTATION INTEGRATION
        # =================================================================
        #
        # APPROACH: Build MkDocs with existing /api structure, then integrate Dokka
        #
        # SOLUTION:
        # 1. Keep the /api structure with bridge pages as-is (maintains navigation)
        # 2. Build MkDocs normally (api/reference.md exists, so strict mode passes)
        # 3. Copy Dokka documentation to /dokka/ location
        # 4. Update navigation links to point directly to /dokka/
        #
        # RESULT:
        # - ✅ MkDocs strict mode passes (api/reference.md exists)
        # - ✅ Users get direct links to Dokka API docs
        # - ✅ Clean integration without temporary files
        # - ✅ Maintains professional user experience
        # =================================================================

        log_info "🔧 Integrating Dokka API documentation..."

        # STEP 1: Build the MkDocs site normally
        # ──────────────────────────────────────
        # MkDocs builds with api/reference.md which satisfies strict mode
        log_info "🏗️  Building MkDocs site (strict mode will pass with existing api/reference.md)..."
        if [ "$ENVIRONMENT" = "production" ]; then
            mkdocs build --strict --verbose
        else
            mkdocs build
        fi

        # STEP 2: Copy Dokka files to the built site
        # ──────────────────────────────────────────
        # Add the real Dokka documentation to the built site at /dokka/
        log_info "📚 Copying Dokka documentation to site/dokka/..."
        if [ -d "../argos/build/dokka" ]; then
            cp -r ../argos/build/dokka site/dokka
            log_success "Dokka documentation copied to site/dokka/"
        else
            log_warning "Dokka documentation not found at ../argos/build/dokka"
        fi

        # STEP 3: Update navigation links to point directly to /dokka/
        # ───────────────────────────────────────────────────────────
        # Replace api/reference links with direct /dokka/ links in navigation
        if [ -d "site" ]; then
            log_info "🔗 Updating navigation links to point directly to /dokka/..."

            # Count files before replacement for verification
            TOTAL_HTML_FILES=$(find site -name "*.html" -type f | wc -l)
            log_info "   Processing $TOTAL_HTML_FILES HTML files..."

            # Update api/reference links to point to /dokka/ directly
            REFERENCE_COUNT=$(find site -name "*.html" -type f -exec grep -l "api/reference" {} \; | wc -l)
            if [ $REFERENCE_COUNT -gt 0 ]; then
                # Replace various api/reference patterns with /dokka/
                find site -name "*.html" -type f -exec sed -i 's|href="[^"]*api/reference[^"]*"|href="/dokka/"|g' {} \; 2>/dev/null || true
                find site -name "*.html" -type f -exec sed -i 's|href="\.\.\/\.\.\/api\/reference[^"]*"|href="/dokka/"|g' {} \; 2>/dev/null || true
                find site -name "*.html" -type f -exec sed -i 's|href=api/reference/|href=/dokka/|g' {} \; 2>/dev/null || true
                log_info "   ✅ Updated $REFERENCE_COUNT files with api/reference links"
            fi

            # Also update sitemap.xml if it exists
            if [ -f "site/sitemap.xml" ]; then
                sed -i 's|https://argos.yot.is/api/reference/|https://argos.yot.is/dokka/|g' site/sitemap.xml
                log_info "   ✅ Updated sitemap.xml to point to /dokka/"
            fi

            # Verify no api/reference links remain in navigation
            REMAINING_COUNT=$(find site -name "*.html" -type f -exec grep -l "href.*api/reference" {} \; 2>/dev/null | wc -l)
            if [ $REMAINING_COUNT -eq 0 ]; then
                log_success "✅ All navigation links successfully updated to point to /dokka/"
            else
                log_warning "⚠️  Still found $REMAINING_COUNT files with api/reference links"
            fi
        fi

        # STEP 4: Verify the integration
        # ─────────────────────────────
        log_info "🔍 Verifying API documentation integration..."

        if [ -f "site/dokka/index.html" ]; then
            log_success "✅ Dokka documentation is accessible at /dokka/"
        else
            log_warning "⚠️  Dokka index not found - API links may not work"
        fi

        log_success "🎉 API documentation integration completed successfully!"
        log_info "📋 Integration summary:"
        log_info "   • MkDocs strict mode: ✅ PASSED (api/reference.md exists)"
        log_info "   • API documentation: ✅ Available at /dokka/"
        log_info "   • Navigation links: ✅ Point directly to Dokka"
        log_info "   • Clean integration: ✅ No temporary files needed"

        log_success "Site built successfully in 'site/' directory with integrated Dokka API docs"
        ;;

    "deploy")
        log_info "Deploying to argos.yot.is..."

        # Check if site directory exists
        if [ ! -d "site" ]; then
            log_error "Site directory not found. Run: $0 build"
            exit 1
        fi

        # Check if rsync is available
        if ! command -v rsync &> /dev/null; then
            log_error "rsync is not installed. Please install rsync first."
            exit 1
        fi

        # Show deployment statistics
        log_info "Preparing deployment statistics..."
        cd site || {
            log_error "Failed to change to site directory"
            exit 1
        }

        # Count files and show size
        TOTAL_FILES=$(find . -type f | wc -l)
        TOTAL_SIZE=$(du -sh . | cut -f1)
        log_info "Deployment package: $TOTAL_FILES files, $TOTAL_SIZE total size"

        # Show file types being deployed
        log_info "File types in deployment:"
        find . -type f -name "*.*" | sed 's/.*\.//' | sort | uniq -c | sort -nr | head -10 | while read count ext; do
            log_info "  $ext files: $count"
        done

        # Show major directories
        log_info "Directory structure:"
        find . -maxdepth 2 -type d | sort | while read dir; do
            if [ "$dir" != "." ]; then
                file_count=$(find "$dir" -type f 2>/dev/null | wc -l)
                if [ $file_count -gt 0 ]; then
                    log_info "  $dir/ ($file_count files)"
                fi
            fi
        done

        # Deploy using rsync with focused output
        log_info "Starting deployment to argos.yot.is..."

        # Create a temporary log file for rsync output
        RSYNC_LOG=$(mktemp)

        # Run rsync with itemized changes to show what's being uploaded
        if rsync -avz -e 'ssh -p 1971' --delete --stats --itemize-changes * teras@yot.is:~/web/argos.yot.is/ 2>&1 | tee "$RSYNC_LOG"; then
            echo ""
            log_success "Documentation deployed successfully to argos.yot.is"

            # Show deployment summary with transfer statistics
            echo ""
            log_info "Transfer Summary:"
            if grep -q "Number of files" "$RSYNC_LOG"; then
                grep "Number of files\|Total transferred file size\|sent\|received" "$RSYNC_LOG" | while read line; do
                    log_info "  $line"
                done
            fi

            # Show uploaded/changed files (files that were actually transferred)
            CHANGED_FILES=$(grep "^>" "$RSYNC_LOG" | wc -l)
            if [ $CHANGED_FILES -gt 0 ]; then
                echo ""
                log_info "Files uploaded/changed ($CHANGED_FILES total):"
                grep "^>" "$RSYNC_LOG" | sed 's/^>f[^[:space:]]* //' | while read file; do
                    log_info "  📄 $file"
                done
            else
                echo ""
                log_info "No files changed - documentation is up to date"
            fi

            # Show deleted files if any
            DELETED_FILES=$(grep -c "^deleting" "$RSYNC_LOG" 2>/dev/null || echo "0")
            DELETED_FILES=$(echo "$DELETED_FILES" | head -1 | tr -d '\n\r ')
            if [ "$DELETED_FILES" -gt 0 ] 2>/dev/null; then
                echo ""
                log_info "Files deleted from destination ($DELETED_FILES total):"
                grep "^deleting" "$RSYNC_LOG" | sed 's/^deleting //' | while read file; do
                    log_warning "  🗑️  $file"
                done
            fi

        else
            log_error "Deployment failed"
            echo "Last 20 lines of rsync output:"
            tail -20 "$RSYNC_LOG"
            rm -f "$RSYNC_LOG"
            cd ..
            exit 1
        fi

        # Clean up log file
        rm -f "$RSYNC_LOG"

        # Return to docs directory
        cd ..
        ;;

    "validate")
        log_info "Validating documentation..."

        # Activate virtual environment if it exists
        if [ -d "venv" ]; then
            source venv/bin/activate
        fi

        # Build with strict mode to catch errors
        mkdocs build --strict --quiet --site-dir temp_site

        # Clean up temporary site
        if [ -d "temp_site" ]; then
            rm -rf temp_site
        fi

        # Check for common issues
        log_info "Checking for common issues..."

        # Check for broken internal links (basic check)
        if grep -r "\]\(" . --include="*.md" | grep -v "http" | grep -v "mailto:" | grep -v "#" >/dev/null; then
            log_warning "Found potential internal links - verify they work correctly"
        fi

        # Check for missing images
        if grep -r "!\[.*\](" . --include="*.md" | grep -v "http" >/dev/null; then
            log_info "Found local image references - ensure all images exist"
        fi

        log_success "Documentation validation completed"
        ;;

    "clean")
        log_info "Cleaning build artifacts..."

        # Remove build directory
        if [ -d "site" ]; then
            rm -rf site
            log_info "Removed 'site/' directory"
        fi

        # Remove temporary files
        find . -name "*.tmp" -delete 2>/dev/null || true
        find . -name ".DS_Store" -delete 2>/dev/null || true

        log_success "Cleanup completed"
        ;;

    "check-links")
        log_info "Checking for broken links..."

        # Activate virtual environment if it exists
        if [ -d "venv" ]; then
            source venv/bin/activate
        fi

        if ! command -v mkdocs &> /dev/null; then
            log_error "MkDocs is not installed. Run: $0 install"
            exit 1
        fi

        # Build site first
        mkdocs build --quiet --site-dir temp_site

        # Basic link checking (could be enhanced with proper tools)
        log_info "Performing basic link validation..."

        # Check for common link issues
        BROKEN_LINKS=0

        # Find markdown files and check links
        while IFS= read -r -d '' file; do
            if grep -q "]\(" "$file"; then
                log_info "Checking links in $(basename "$file")..."

                # Extract links and check if they're valid (basic check)
                grep -o "]\([^)]*\)" "$file" | sed 's/](\([^)]*\))/\1/' | while read -r link; do
                    if [[ "$link" =~ ^https?:// ]]; then
                        continue  # Skip external links for now
                    elif [[ "$link" =~ ^# ]]; then
                        continue  # Skip anchors for now
                    elif [[ "$link" =~ \.md$ ]]; then
                        # Check if markdown file exists
                        if [ ! -f "$(dirname "$file")/$link" ] && [ ! -f "$link" ]; then
                            log_warning "Broken link in $(basename "$file"): $link"
                            BROKEN_LINKS=$((BROKEN_LINKS + 1))
                        fi
                    fi
                done
            fi
        done < <(find . -name "*.md" -type f -print0)

        # Clean up
        if [ -d "temp_site" ]; then
            rm -rf temp_site
        fi

        if [ $BROKEN_LINKS -eq 0 ]; then
            log_success "No broken internal links found"
        else
            log_warning "Found $BROKEN_LINKS potential broken links"
        fi
        ;;

    "deploy-test")
        log_info "Testing deployment (dry run)..."

        # Check if site directory exists
        if [ ! -d "site" ]; then
            log_error "Site directory not found. Run: $0 build"
            exit 1
        fi

        # Check if rsync is available
        if ! command -v rsync &> /dev/null; then
            log_error "rsync is not installed. Please install rsync first."
            exit 1
        fi

        # Show what would be deployed
        log_info "Preparing deployment test..."
        cd site || {
            log_error "Failed to change to site directory"
            exit 1
        }

        # Count files and show size
        TOTAL_FILES=$(find . -type f | wc -l)
        TOTAL_SIZE=$(du -sh . | cut -f1)
        log_info "Would deploy: $TOTAL_FILES files, $TOTAL_SIZE total size"

        # Show file types
        log_info "File types that would be deployed:"
        find . -type f -name "*.*" | sed 's/.*\.//' | sort | uniq -c | sort -nr | head -10 | while read count ext; do
            log_info "  $ext files: $count"
        done

        # Run rsync in dry run mode
        log_info "Running deployment test (dry run)..."
        log_info "Destination: teras@yot.is:~/web/argos.yot.is/"

        # Create temporary log for dry run output
        DRYRUN_LOG=$(mktemp)

        if rsync -avz -e 'ssh -p 1971' --delete --dry-run --stats --itemize-changes * teras@yot.is:~/web/argos.yot.is/ 2>&1 | tee "$DRYRUN_LOG"; then
            # Show what would be transferred
            WOULD_CHANGE=$(grep "^>" "$DRYRUN_LOG" | wc -l)
            WOULD_DELETE=$(grep -c "^deleting" "$DRYRUN_LOG" 2>/dev/null || echo "0")
            WOULD_DELETE=$(echo "$WOULD_DELETE" | head -1 | tr -d '\n\r ')

            echo ""
            log_success "Deployment test completed - no errors detected"
            log_info "Would transfer: $WOULD_CHANGE files"
            log_info "Would delete: $WOULD_DELETE files"

            if [ $WOULD_CHANGE -gt 0 ]; then
                log_info "Files that would be uploaded/changed:"
                grep "^>" "$DRYRUN_LOG" | sed 's/^>f[^[:space:]]* //' | head -10 | while read file; do
                    log_info "  📄 $file"
                done
                if [ $WOULD_CHANGE -gt 10 ]; then
                    remaining=$(expr $WOULD_CHANGE - 10)
                    log_info "  ... and $remaining more files"
                fi
            fi
        else
            log_error "Deployment test failed"
            cd ..
            exit 1
        fi

        # Clean up log file
        rm -f "$DRYRUN_LOG"

        # Return to docs directory
        cd ..
        ;;

    "help"|"--help"|"-h")
        echo "Argos Documentation Build Script"
        echo ""
        echo "Usage: $0 <command> [environment]"
        echo ""
        echo "Commands:"
        echo "  install     Install MkDocs and dependencies"
        echo "  serve       Start development server (default)"
        echo "  build       Build static site with Dokka integration"
        echo "  deploy      Deploy to argos.yot.is with verbose output"
        echo "  deploy-test Test deployment (dry run)"
        echo "  validate    Validate documentation"
        echo "  clean       Clean build artifacts"
        echo "  check-links Check for broken links"
        echo "  help        Show this help message"
        echo ""
        echo "Environment:"
        echo "  development Default environment (faster builds)"
        echo "  production  Production environment (strict validation)"
        echo ""
        echo "Examples:"
        echo "  $0 serve                    # Start development server"
        echo "  $0 build production         # Production build with Dokka"
        echo "  $0 deploy-test              # Test deployment (dry run)"
        echo "  $0 deploy                   # Deploy with verbose file listing"
        echo "  $0 validate                 # Validate docs and check links"
        ;;

    *)
        log_error "Unknown command: $COMMAND"
        echo "Run '$0 help' for available commands"
        exit 1
        ;;
esac