package fyi.sererr.proto;

import com.google.protobuf.CodedOutputStream;
import fyi.sererr.CapturedError;
import fyi.sererr.DebugInfo;
import fyi.sererr.ExceptionMechanism;
import fyi.sererr.StackFrame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Converts between sererr's plain Java types
 * ({@link fyi.sererr.CapturedError} et al.) and the proto-generated
 * types ({@code fyi.sererr.v1.CapturedError} et al. — generated from
 * {@code proto/sererr/v1/sererr.proto}; the file path mirrors the
 * proto package so future versions can live at {@code v2/} etc.).
 *
 * <p>The {@link #encode(CapturedError)} method uses
 * {@link CodedOutputStream#useDeterministicSerialization()} so map
 * fields ({@code ExceptionMechanism.data}) emit in sorted-key order;
 * combined with the plain types' use of {@link java.util.TreeMap}, the
 * encoded bytes are byte-equivalent to the Rust-generated fixtures.
 */
public final class ProtoAdapter {
    private ProtoAdapter() {}

    // ---------- plain → proto ----------

    public static fyi.sererr.v1.StackFrame toProto(StackFrame f) {
        return fyi.sererr.v1.StackFrame.newBuilder()
                .setFunction(f.function())
                .setModule(f.module())
                .setPackage(f.pkg())
                .setFile(f.file())
                .setAbsPath(f.absPath())
                .setLine(f.line())
                .setContextLine(f.contextLine())
                .addAllPreContext(f.preContext())
                .addAllPostContext(f.postContext())
                .setSourceLink(f.sourceLink())
                .setInApp(f.inApp())
                .build();
    }

    public static fyi.sererr.v1.ExceptionMechanism toProto(ExceptionMechanism m) {
        return fyi.sererr.v1.ExceptionMechanism.newBuilder()
                .setType(m.type())
                .setDescription(m.description())
                .setHandled(m.handled())
                .setSynthetic(m.synthetic())
                .setHelpLink(m.helpLink())
                .setSource(m.source())
                .setExceptionId(m.exceptionId())
                .setParentId(m.parentId())
                .setIsExceptionGroup(m.isExceptionGroup())
                // ExceptionMechanism.data is already a TreeMap; copy
                // into a fresh TreeMap to be safe in case a caller
                // passed an alternative SortedMap impl with unstable
                // ordering.
                .putAllData(new TreeMap<>(m.data()))
                .build();
    }

    public static fyi.sererr.v1.CapturedError toProto(CapturedError e) {
        fyi.sererr.v1.CapturedError.Builder b = fyi.sererr.v1.CapturedError.newBuilder()
                .setType(e.type())
                .setMessage(e.message())
                .setRelease(e.release())
                .setServerName(e.serverName());
        for (StackFrame f : e.frames()) {
            b.addFrames(toProto(f));
        }
        e.mechanism().ifPresent(m -> b.setMechanism(toProto(m)));
        return b.build();
    }

    public static com.google.rpc.DebugInfo toProto(DebugInfo di) {
        return com.google.rpc.DebugInfo.newBuilder()
                .addAllStackEntries(di.stackEntries())
                .setDetail(di.detail())
                .build();
    }

    // ---------- proto → plain ----------

    public static StackFrame fromProto(fyi.sererr.v1.StackFrame f) {
        return new StackFrame(
                f.getFunction(),
                f.getModule(),
                f.getPackage(),
                f.getFile(),
                f.getAbsPath(),
                f.getLine(),
                f.getContextLine(),
                List.copyOf(f.getPreContextList()),
                List.copyOf(f.getPostContextList()),
                f.getSourceLink(),
                f.getInApp());
    }

    public static ExceptionMechanism fromProto(fyi.sererr.v1.ExceptionMechanism m) {
        SortedMap<String, String> data = new TreeMap<>(m.getDataMap());
        return new ExceptionMechanism(
                m.getType(),
                m.getDescription(),
                m.getHandled(),
                m.getSynthetic(),
                m.getHelpLink(),
                m.getSource(),
                m.getExceptionId(),
                m.getParentId(),
                m.getIsExceptionGroup(),
                data);
    }

    public static CapturedError fromProto(fyi.sererr.v1.CapturedError e) {
        var frames = new ArrayList<StackFrame>(e.getFramesCount());
        for (var f : e.getFramesList()) {
            frames.add(fromProto(f));
        }
        Optional<ExceptionMechanism> mech = e.hasMechanism()
                ? Optional.of(fromProto(e.getMechanism()))
                : Optional.empty();
        return new CapturedError(
                e.getType(), e.getMessage(), frames, mech, e.getRelease(), e.getServerName());
    }

    public static DebugInfo fromProto(com.google.rpc.DebugInfo di) {
        return new DebugInfo(List.copyOf(di.getStackEntriesList()), di.getDetail());
    }

    // ---------- deterministic encoding ----------

    /**
     * Serialize a {@link CapturedError} to deterministic proto bytes.
     *
     * <p>"Deterministic" here means: map fields emit in sorted-key
     * order. Combined with {@link ExceptionMechanism}'s sorted
     * {@code data} field, this yields byte-equivalent output across
     * languages, which the cross-language conformance fixtures verify.
     */
    public static byte[] encode(CapturedError e) {
        return encodeProto(toProto(e));
    }

    /** As {@link #encode(CapturedError)} but for an already-built proto. */
    public static byte[] encodeProto(fyi.sererr.v1.CapturedError proto) {
        try (var stream = new ByteArrayOutputStream()) {
            CodedOutputStream out = CodedOutputStream.newInstance(stream);
            out.useDeterministicSerialization();
            proto.writeTo(out);
            out.flush();
            return stream.toByteArray();
        } catch (IOException ioe) {
            throw new RuntimeException("encoding to byte array failed", ioe);
        }
    }

    /** Decode bytes back into a plain {@link CapturedError}. */
    public static CapturedError decode(byte[] bytes) {
        try {
            return fromProto(fyi.sererr.v1.CapturedError.parseFrom(bytes));
        } catch (com.google.protobuf.InvalidProtocolBufferException ipbe) {
            throw new RuntimeException("decode failed", ipbe);
        }
    }
}
