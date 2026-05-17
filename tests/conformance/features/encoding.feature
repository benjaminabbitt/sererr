Feature: Encoding round-trip
  As a sererr consumer
  I want every language to serialize identical inputs to identical bytes
  So that producers and consumers in different languages interoperate

  Background:
    Given the canonical sererr.v1 proto schema

  Scenario Outline: Fixture encodes to canonical bytes
    Given a fixture "<fixture>"
    When I construct the CapturedError per the fixture's JSON descriptor
    And I serialize it via the proto adapter
    Then the encoded bytes match "fixtures/<fixture>.pb"

    Examples:
      | fixture                  |
      | 0001-simple              |
      | 0002-empty-chain         |
      | 0003-three-deep          |
      | 0004-source-context      |
      | 0005-mechanism-data      |

  Scenario: Empty CapturedError encodes as empty bytes
    Given a default-initialized CapturedError (all zero values)
    When I serialize it
    Then the encoded bytes are empty

  Scenario: Default values round-trip
    Given a fixture "0001-simple"
    When I serialize it
    And I deserialize the bytes back to a CapturedError
    Then the result equals the input field-by-field
