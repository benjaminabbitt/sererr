from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from typing import ClassVar as _ClassVar, Iterable as _Iterable, Mapping as _Mapping, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class StackFrame(_message.Message):
    __slots__ = ("function", "module", "package", "file", "abs_path", "line", "context_line", "pre_context", "post_context", "source_link", "in_app")
    FUNCTION_FIELD_NUMBER: _ClassVar[int]
    MODULE_FIELD_NUMBER: _ClassVar[int]
    PACKAGE_FIELD_NUMBER: _ClassVar[int]
    FILE_FIELD_NUMBER: _ClassVar[int]
    ABS_PATH_FIELD_NUMBER: _ClassVar[int]
    LINE_FIELD_NUMBER: _ClassVar[int]
    CONTEXT_LINE_FIELD_NUMBER: _ClassVar[int]
    PRE_CONTEXT_FIELD_NUMBER: _ClassVar[int]
    POST_CONTEXT_FIELD_NUMBER: _ClassVar[int]
    SOURCE_LINK_FIELD_NUMBER: _ClassVar[int]
    IN_APP_FIELD_NUMBER: _ClassVar[int]
    function: str
    module: str
    package: str
    file: str
    abs_path: str
    line: int
    context_line: str
    pre_context: _containers.RepeatedScalarFieldContainer[str]
    post_context: _containers.RepeatedScalarFieldContainer[str]
    source_link: str
    in_app: bool
    def __init__(self, function: _Optional[str] = ..., module: _Optional[str] = ..., package: _Optional[str] = ..., file: _Optional[str] = ..., abs_path: _Optional[str] = ..., line: _Optional[int] = ..., context_line: _Optional[str] = ..., pre_context: _Optional[_Iterable[str]] = ..., post_context: _Optional[_Iterable[str]] = ..., source_link: _Optional[str] = ..., in_app: bool = ...) -> None: ...

class ExceptionMechanism(_message.Message):
    __slots__ = ("type", "description", "handled", "synthetic", "help_link", "source", "exception_id", "parent_id", "is_exception_group", "data")
    class DataEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: str
        def __init__(self, key: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...
    TYPE_FIELD_NUMBER: _ClassVar[int]
    DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    HANDLED_FIELD_NUMBER: _ClassVar[int]
    SYNTHETIC_FIELD_NUMBER: _ClassVar[int]
    HELP_LINK_FIELD_NUMBER: _ClassVar[int]
    SOURCE_FIELD_NUMBER: _ClassVar[int]
    EXCEPTION_ID_FIELD_NUMBER: _ClassVar[int]
    PARENT_ID_FIELD_NUMBER: _ClassVar[int]
    IS_EXCEPTION_GROUP_FIELD_NUMBER: _ClassVar[int]
    DATA_FIELD_NUMBER: _ClassVar[int]
    type: str
    description: str
    handled: bool
    synthetic: bool
    help_link: str
    source: str
    exception_id: int
    parent_id: int
    is_exception_group: bool
    data: _containers.ScalarMap[str, str]
    def __init__(self, type: _Optional[str] = ..., description: _Optional[str] = ..., handled: bool = ..., synthetic: bool = ..., help_link: _Optional[str] = ..., source: _Optional[str] = ..., exception_id: _Optional[int] = ..., parent_id: _Optional[int] = ..., is_exception_group: bool = ..., data: _Optional[_Mapping[str, str]] = ...) -> None: ...

class CapturedError(_message.Message):
    __slots__ = ("type", "message", "frames", "mechanism", "release", "server_name")
    TYPE_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    FRAMES_FIELD_NUMBER: _ClassVar[int]
    MECHANISM_FIELD_NUMBER: _ClassVar[int]
    RELEASE_FIELD_NUMBER: _ClassVar[int]
    SERVER_NAME_FIELD_NUMBER: _ClassVar[int]
    type: str
    message: str
    frames: _containers.RepeatedCompositeFieldContainer[StackFrame]
    mechanism: ExceptionMechanism
    release: str
    server_name: str
    def __init__(self, type: _Optional[str] = ..., message: _Optional[str] = ..., frames: _Optional[_Iterable[_Union[StackFrame, _Mapping]]] = ..., mechanism: _Optional[_Union[ExceptionMechanism, _Mapping]] = ..., release: _Optional[str] = ..., server_name: _Optional[str] = ...) -> None: ...
