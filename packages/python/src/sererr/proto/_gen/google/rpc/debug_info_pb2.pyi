from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Optional as _Optional

DESCRIPTOR: _descriptor.FileDescriptor

class DebugInfo(_message.Message):
    __slots__ = ("stack_entries", "detail")
    STACK_ENTRIES_FIELD_NUMBER: _ClassVar[int]
    DETAIL_FIELD_NUMBER: _ClassVar[int]
    stack_entries: _containers.RepeatedScalarFieldContainer[str]
    detail: str
    def __init__(self, stack_entries: _Optional[_Iterable[str]] = ..., detail: _Optional[str] = ...) -> None: ...
