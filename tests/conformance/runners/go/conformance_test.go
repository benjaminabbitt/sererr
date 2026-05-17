// Package conformance runs the cross-language Cucumber corpus against
// the Go sererr package. The same Gherkin features at
// tests/conformance/features/ are exercised by every language's
// runner — sererr's wire-format contract is the executable spec.
//
// Run with `just test` (from this directory) or via the top-level
// tests/conformance/run.sh orchestrator. Required env vars:
//   - SERERR_FEATURES_DIR — path to features/
//   - SERERR_FIXTURES_DIR — path to fixtures/
package conformance

import (
	"os"
	"testing"

	"github.com/cucumber/godog"
)

func TestConformance(t *testing.T) {
	featuresDir := os.Getenv("SERERR_FEATURES_DIR")
	if featuresDir == "" {
		t.Fatal("SERERR_FEATURES_DIR env var must point at tests/conformance/features")
	}
	if os.Getenv("SERERR_FIXTURES_DIR") == "" {
		t.Fatal("SERERR_FIXTURES_DIR env var must point at tests/conformance/fixtures")
	}

	suite := godog.TestSuite{
		ScenarioInitializer: InitializeScenario,
		Options: &godog.Options{
			Format:        "pretty",
			Paths:         []string{featuresDir},
			Strict:        true,
			TestingT:      t,
			StopOnFailure: false,
		},
	}
	if suite.Run() != 0 {
		t.Fatal("conformance scenarios failed")
	}
}
