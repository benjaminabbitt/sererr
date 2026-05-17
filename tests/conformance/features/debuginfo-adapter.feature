Feature: DebugInfo adapter
  sererr captures convert losslessly into google.rpc.DebugInfo-compatible
  shape so gRPC error-model tooling can ingest them.

  Scenario: Empty chain produces empty DebugInfo
    Given an empty chain
    When I call to_debug_info
    Then stack_entries is empty
    And detail is empty

  Scenario: Single error produces one type/message in detail
    Given a single CapturedError with type "MyError" and message "oops"
    When I call to_debug_info
    Then detail equals "MyError: oops"

  Scenario: Chained errors join with "Caused by:"
    Given a chain "inner" caused-by "outer"
    When I call to_debug_info
    Then detail contains "outer"
    And detail contains "Caused by"
    And detail contains "inner"

  Scenario: Frame lines are formatted "  at <function> (<file>:<line>)"
    Given a CapturedError with one frame:
      | function | file        | line |
      | doit     | src/x.rs    | 12   |
    When I call to_debug_info
    Then stack_entries contains "  at doit (src/x.rs:12)"
