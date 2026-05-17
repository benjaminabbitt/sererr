"""Behave entrypoint for the Python conformance runner.

Drives the shared cucumber corpus at ``tests/conformance/features/``
against the Python step implementations in ``./features/steps/``.

We run behave programmatically — not via the ``behave`` CLI — because
behave's default ``setup_paths`` requires the steps directory to live
inside (or as an ancestor of) the features directory. The corpus and
the per-language runners are deliberately split across the repo, so we
override ``setup_paths`` to set:

- ``config.paths`` = features dir (from ``SERERR_FEATURES_DIR``)
- ``base_dir``     = this runner's directory (where ``steps/`` lives)

Mirrors the Rust runner's contract: same env vars, same step library,
same 15 scenarios.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

from behave.configuration import Configuration
from behave.runner import Runner


class ConformanceRunner(Runner):
    """Runner that decouples the features path from the steps path.

    The standard behave runner enforces a strict directory layout
    (``<base>/features/*.feature`` and ``<base>/steps/*.py``). The
    sererr conformance corpus lives at
    ``tests/conformance/features/`` while this runner's steps live at
    ``tests/conformance/runners/python/features/steps/`` — two
    different subtrees. We override ``setup_paths`` to point behave at
    both.
    """

    def setup_paths(self) -> None:
        features_env = os.environ.get("SERERR_FEATURES_DIR")
        if not features_env:
            raise RuntimeError(
                "SERERR_FEATURES_DIR must be set to the conformance features directory"
            )
        features_dir = Path(features_env).resolve()
        if not features_dir.is_dir():
            raise RuntimeError(f"SERERR_FEATURES_DIR does not exist: {features_dir}")

        runner_dir = Path(__file__).resolve().parent
        steps_dir = runner_dir / "features" / "steps"
        if not steps_dir.is_dir():
            raise RuntimeError(f"steps directory missing: {steps_dir}")

        # config.paths is the source of feature files (used by
        # feature_locations()); base_dir is the root behave uses to
        # find steps/ and environment.py.
        self.config.paths = [str(features_dir)]
        self.config.base_dir = str(runner_dir / "features")
        self.base_dir = str(runner_dir / "features")
        self.path_manager.add(self.base_dir)
        if self.base_dir != os.getcwd():
            self.path_manager.add(os.getcwd())


def main(argv: list[str] | None = None) -> int:
    argv = list(argv if argv is not None else sys.argv[1:])
    # If no explicit args, default to behaviors the spec asks for.
    if not argv:
        argv = ["--no-source", "--tags=~@wip"]
    config = Configuration(argv)
    # behave's CLI normally fills these in from default_format / a
    # SummaryReporter; we mirror that here since we are bypassing the
    # standard ``behave.__main__.run_behave`` entrypoint.
    if not config.format:
        config.format = [config.default_format]
    runner = ConformanceRunner(config)
    failed = runner.run()
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
