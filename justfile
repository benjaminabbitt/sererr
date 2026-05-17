# sererr — host justfile (composed).
#
# Composition pattern:
#   - Per-language tasks live in `packages/<lang>/justfile`.
#   - This file is a thin host wrapper that picks the right toolchain
#     container, mounts the repo, and shells into `packages/<lang>` to
#     invoke that language's justfile.
#   - Per-language recipes (build, test, mutants, proto-gen, publish)
#     are uniform across languages so cross-language orchestration is
#     trivial.

set shell := ["bash", "-c"]

# Discover the repo root via git so the justfile is portable from any
# subdirectory and from inside the toolchain containers (which mount
# .git as part of the workspace).
TOP := `git rev-parse --show-toplevel`
LANGS := "csharp go java kotlin python rust typescript"

CONTAINER_CMD := `command -v docker 2>/dev/null || command -v podman 2>/dev/null || echo ""`
CONTAINER_RUN := CONTAINER_CMD + " run --rm -u $(id -u):$(id -g)"
IMAGE_PREFIX := "ghcr.io/sererr/sererr-"
IMAGE_TAG := "latest"

default:
    @just --list

# === image lifecycle ===

# Build the toolchain image for one language.
build-image lang:
    {{CONTAINER_CMD}} build -t {{IMAGE_PREFIX}}{{lang}}:{{IMAGE_TAG}} \
        -f "{{TOP}}/containers/{{lang}}/Containerfile" \
        "{{TOP}}/containers/{{lang}}"

# Build all toolchain images.
build-images:
    for lang in {{LANGS}}; do just build-image $lang; done

# === entry helper ===

# Run `just <task>` inside the container for <lang>, against the
# per-language `packages/<lang>/justfile`. The composed pattern means
# every language exposes the same task surface (build / test / mutants
# / proto-gen / publish / lint / fmt / check).
_in lang +TASKS:
    #!/usr/bin/env bash
    set -euo pipefail
    if [ "${IN_SERERR_CONTAINER:-}" = "true" ]; then
        # Already inside a container; invoke the package justfile directly.
        cd "{{TOP}}/packages/{{lang}}" && just {{TASKS}}
    elif [ "${SERERR_REQUIRE_CONTAINER:-}" != "true" ] && \
         ! {{CONTAINER_CMD}} image inspect "{{IMAGE_PREFIX}}{{lang}}:{{IMAGE_TAG}}" >/dev/null 2>&1; then
        # Container image not built locally; fall back to host toolchain.
        # Force the container path by setting SERERR_REQUIRE_CONTAINER=true
        # (CI does this; local dev typically doesn't).
        cd "{{TOP}}/packages/{{lang}}" && just {{TASKS}}
    else
        # Pass git safe.directory via env vars rather than gitconfig so
        # we don't need a writable HOME in the container. Redirect every
        # toolchain's package cache to /workspace/.cache so the
        # host-UID process can write to it (the default cache locations
        # inside the image are owned by root and not writable).
        {{CONTAINER_RUN}} \
            -v "{{TOP}}:/workspace:Z" \
            --tmpfs /tmp:exec,size=4g \
            -w "/workspace/packages/{{lang}}" \
            -e IN_SERERR_CONTAINER=true \
            -e HOME=/tmp \
            -e GIT_CONFIG_COUNT=1 \
            -e GIT_CONFIG_KEY_0=safe.directory \
            -e GIT_CONFIG_VALUE_0=/workspace \
            -e XDG_CACHE_HOME=/workspace/.cache \
            -e CARGO_HOME=/workspace/.cache/cargo \
            -e GOCACHE=/workspace/.cache/go-build \
            -e GOMODCACHE=/workspace/.cache/go-mod \
            -e GRADLE_USER_HOME=/workspace/.cache/gradle \
            -e NUGET_PACKAGES=/workspace/.cache/nuget \
            -e npm_config_cache=/workspace/.cache/npm \
            {{IMAGE_PREFIX}}{{lang}}:{{IMAGE_TAG}} \
            just {{TASKS}}
    fi

# === uniform per-language task surface ===

build lang:
    just _in {{lang}} build

test lang:
    just _in {{lang}} test

mutants lang:
    just _in {{lang}} mutants

# Coverage measurement for one language. Each language's `coverage`
# recipe writes its native report format (lcov, cobertura, html);
# results land under the language's own dir (target/llvm-cov,
# htmlcov, build/reports/jacoco, etc.).
coverage lang:
    just _in {{lang}} coverage

proto-gen lang:
    just _in {{lang}} proto-gen

lint lang:
    just _in {{lang}} lint

fmt lang:
    just _in {{lang}} fmt

check lang:
    just _in {{lang}} check

publish lang version:
    just _in {{lang}} publish {{version}}

# Build the distributable artifact for one language. Each language's
# `package` recipe writes its artifact to {{TOP}}/dist/<lang>/ so the
# orchestrator can collect them in one place.
package lang:
    just _in {{lang}} package

# === cross-language orchestrators ===

build-all:
    for lang in {{LANGS}}; do just build $lang; done

test-all:
    for lang in {{LANGS}}; do just test $lang; done

mutants-all:
    for lang in {{LANGS}}; do just mutants $lang; done

coverage-all:
    for lang in {{LANGS}}; do just coverage $lang; done

check-all:
    for lang in {{LANGS}}; do just check $lang; done

# Build distributable artifacts for every language. Artifacts land in
# {{TOP}}/dist/<lang>/; the directory is gitignored.
package-all:
    rm -rf {{TOP}}/dist
    for lang in {{LANGS}}; do just package $lang; done
    @echo
    @echo "=== artifacts in dist/ ==="
    @find {{TOP}}/dist -type f -printf '%P (%s bytes)\n' | sort

# Remove dist/ and per-package build outputs.
dist-clean:
    rm -rf {{TOP}}/dist
    rm -rf {{TOP}}/packages/rust/target/package
    rm -rf {{TOP}}/packages/python/dist
    rm -rf {{TOP}}/packages/java/build/libs
    rm -rf {{TOP}}/packages/kotlin/build/libs
    rm -rf {{TOP}}/packages/csharp/bin/Release
    rm -f {{TOP}}/packages/typescript/*.tgz

# csharp + java + kotlin generate protos as part of `build`; skip them
# in the explicit proto-gen-all loop.
proto-gen-all:
    for lang in go python rust typescript; do just proto-gen $lang; done

# Cross-language conformance: every language encodes a fixture, every
# other language decodes; assert equivalence on the wire bytes.
#
# Override the language set via `SERERR_CONFORMANCE_LANGS="rust python" just conformance`.
conformance:
    #!/usr/bin/env bash
    set -euo pipefail
    export SERERR_FEATURES_DIR="{{TOP}}/tests/conformance/features"
    export SERERR_FIXTURES_DIR="{{TOP}}/tests/conformance/fixtures"
    LANGS="${SERERR_CONFORMANCE_LANGS:-{{LANGS}}}"
    failed=()
    for lang in $LANGS; do
        runner_dir="{{TOP}}/tests/conformance/runners/${lang}"
        if [ ! -f "${runner_dir}/justfile" ]; then
            echo "SKIP ${lang}: no runner at ${runner_dir}"
            continue
        fi
        echo "=== conformance: ${lang} ==="
        if (cd "${runner_dir}" && just test); then
            echo "=== ${lang}: OK ==="
        else
            echo "=== ${lang}: FAIL ==="
            failed+=("$lang")
        fi
    done
    if [ ${#failed[@]} -gt 0 ]; then
        echo
        echo "FAILED: ${failed[*]}"
        exit 1
    fi
    echo
    echo "all conformance runners passed"

# Run conformance for a single language.
conformance-one lang:
    SERERR_FEATURES_DIR="{{TOP}}/tests/conformance/features" \
    SERERR_FIXTURES_DIR="{{TOP}}/tests/conformance/fixtures" \
    bash -c "cd '{{TOP}}/tests/conformance/runners/{{lang}}' && just test"

# === proto ===

proto-lint:
    {{CONTAINER_RUN}} -v "{{TOP}}/proto:/workspace:Z" -w /workspace \
        bufbuild/buf:latest lint
