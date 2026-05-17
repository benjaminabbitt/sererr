Feature: Chain semantics
  Sererr chains are flat, most-causal-first, mechanism-linked.

  Scenario: Single error produces a one-entry chain
    Given an error with no source / cause
    When I capture it
    Then the chain length is 1
    And the entry's mechanism has exception_id 0
    And the entry's mechanism has parent_id 0

  Scenario: Three-deep chain has correct mechanism IDs
    Given a chain "inner" caused-by "middle" caused-by "outer"
    When I capture the outermost error
    Then the chain length is 3
    And entry 0 has exception_id 0 and parent_id 0
    And entry 1 has exception_id 1 and parent_id 0
    And entry 2 has exception_id 2 and parent_id 1
    And entry 0 has message "inner"
    And entry 2 has message "outer"

  Scenario: Originating caught error is the last element
    Given a chain "inner" caused-by "outer"
    When I capture the outermost error
    Then the last chain entry has message "outer"

  Scenario: Frame ordering is most-recent-first
    Given a captured error with frames
    When I read the frames
    Then the first frame is the most recent call
