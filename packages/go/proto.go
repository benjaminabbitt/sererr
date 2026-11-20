package sererr

import (
	pb "sererr.fyi/sererr/packages/go/pb/sererr/v1"
)

// ToProto converts a plain StackFrame into its proto wire form.
func (f StackFrame) ToProto() *pb.StackFrame {
	return &pb.StackFrame{
		Function:    f.Function,
		Module:      f.Module,
		Package:     f.Package,
		File:        f.File,
		AbsPath:     f.AbsPath,
		Line:        f.Line,
		ContextLine: f.ContextLine,
		PreContext:  cloneStrings(f.PreContext),
		PostContext: cloneStrings(f.PostContext),
		SourceLink:  f.SourceLink,
		InApp:       f.InApp,
	}
}

// ToProto converts a plain ExceptionMechanism into its proto wire form.
func (m ExceptionMechanism) ToProto() *pb.ExceptionMechanism {
	var data map[string]string
	if m.Data != nil {
		data = make(map[string]string, len(m.Data))
		for k, v := range m.Data {
			data[k] = v
		}
	}
	return &pb.ExceptionMechanism{
		Type:             m.Type,
		Description:      m.Description,
		Handled:          m.Handled,
		Synthetic:        m.Synthetic,
		HelpLink:         m.HelpLink,
		Source:           m.Source,
		ExceptionId:      m.ExceptionId,
		ParentId:         m.ParentId,
		IsExceptionGroup: m.IsExceptionGroup,
		Data:             data,
	}
}

// ToProto converts a plain CapturedError into its proto wire form.
func (c CapturedError) ToProto() *pb.CapturedError {
	frames := make([]*pb.StackFrame, 0, len(c.Frames))
	for _, f := range c.Frames {
		frames = append(frames, f.ToProto())
	}
	var mech *pb.ExceptionMechanism
	if c.Mechanism != nil {
		mech = c.Mechanism.ToProto()
	}
	return &pb.CapturedError{
		Type:       c.Type,
		Message:    c.Message,
		Frames:     frames,
		Mechanism:  mech,
		Release:    c.Release,
		ServerName: c.ServerName,
	}
}

// FromProtoStackFrame converts a proto StackFrame into the plain type.
func FromProtoStackFrame(p *pb.StackFrame) StackFrame {
	if p == nil {
		return StackFrame{}
	}
	return StackFrame{
		Function:    p.GetFunction(),
		Module:      p.GetModule(),
		Package:     p.GetPackage(),
		File:        p.GetFile(),
		AbsPath:     p.GetAbsPath(),
		Line:        p.GetLine(),
		ContextLine: p.GetContextLine(),
		PreContext:  cloneStrings(p.GetPreContext()),
		PostContext: cloneStrings(p.GetPostContext()),
		SourceLink:  p.GetSourceLink(),
		InApp:       p.GetInApp(),
	}
}

// FromProtoExceptionMechanism converts a proto ExceptionMechanism into
// the plain type.
func FromProtoExceptionMechanism(p *pb.ExceptionMechanism) *ExceptionMechanism {
	if p == nil {
		return nil
	}
	var data map[string]string
	if d := p.GetData(); d != nil {
		data = make(map[string]string, len(d))
		for k, v := range d {
			data[k] = v
		}
	}
	return &ExceptionMechanism{
		Type:             p.GetType(),
		Description:      p.GetDescription(),
		Handled:          p.GetHandled(),
		Synthetic:        p.GetSynthetic(),
		HelpLink:         p.GetHelpLink(),
		Source:           p.GetSource(),
		ExceptionId:      p.GetExceptionId(),
		ParentId:         p.GetParentId(),
		IsExceptionGroup: p.GetIsExceptionGroup(),
		Data:             data,
	}
}

// FromProto converts a proto CapturedError into the plain type.
func FromProto(p *pb.CapturedError) CapturedError {
	if p == nil {
		return CapturedError{}
	}
	var frames []StackFrame
	if fs := p.GetFrames(); len(fs) > 0 {
		frames = make([]StackFrame, 0, len(fs))
		for _, f := range fs {
			frames = append(frames, FromProtoStackFrame(f))
		}
	}
	return CapturedError{
		Type:       p.GetType(),
		Message:    p.GetMessage(),
		Frames:     frames,
		Mechanism:  FromProtoExceptionMechanism(p.GetMechanism()),
		Release:    p.GetRelease(),
		ServerName: p.GetServerName(),
	}
}

func cloneStrings(in []string) []string {
	if in == nil {
		return nil
	}
	out := make([]string, len(in))
	copy(out, in)
	return out
}
