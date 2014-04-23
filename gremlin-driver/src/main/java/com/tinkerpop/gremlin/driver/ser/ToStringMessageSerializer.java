package com.tinkerpop.gremlin.driver.ser;

import com.tinkerpop.gremlin.driver.MessageSerializer;
import com.tinkerpop.gremlin.driver.message.RequestMessage;
import com.tinkerpop.gremlin.driver.message.ResponseMessage;
import com.tinkerpop.gremlin.driver.message.ResultCode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serialize results via {@link Object#toString}.  It is important to note that this serializer does not support
 * {@link RequestMessage} deserialization which means that requests cannot be submitted with this type.  This
 * {@link MessageSerializer} can only format results to this format.
 *
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public class ToStringMessageSerializer implements MessageSerializer {

    // todo: better up this regex
    private static final Pattern patternResponse = Pattern.compile("^(.+)>>(\\d+)>>(.+)");
    private static final String TEXT_RESPONSE_FORMAT_WITH_RESULT = "%s>>%s>>%s";

    @Override
    public String serializeRequestAsString(final RequestMessage requestMessage) {
        // todo: may as well support this?
        throw new UnsupportedOperationException(String.format("The %s does not support the %s format.",
                ToStringMessageSerializer.class.getName(), RequestMessage.class.getName()));
    }

    @Override
    public Optional<RequestMessage> deserializeRequest(final String msg) {
        // todo: may as well support this?
        throw new UnsupportedOperationException(String.format("The %s does not support the %s format.",
                ToStringMessageSerializer.class.getName(), RequestMessage.class.getName()));
    }

    @Override
    public String serializeResponseAsString(final ResponseMessage responseMessage) {
        final String requestId = responseMessage.getRequestId() != null ? responseMessage.getRequestId().toString() : "";
        final String result = responseMessage.getResult() != null ? responseMessage.getResult().toString() : "null";
        return String.format(TEXT_RESPONSE_FORMAT_WITH_RESULT, requestId, responseMessage.getCode().getValue(), result);
    }

    @Override
    public Optional<ResponseMessage> deserializeResponse(final String msg) {
        final Matcher matcher = patternResponse.matcher(msg);
        if (matcher.matches()) {
            // todo: error handling
            return Optional.of(ResponseMessage.create(UUID.fromString(matcher.group(1)))
                    .code(ResultCode.getFromValue(Integer.parseInt(matcher.group(2))))
                    .result(matcher.group(3))
                    .build());
        } else {
            // todo: for now
            return Optional.empty();
        }
    }

    @Override
    public ByteBuf serializeRequestAsBinary(final RequestMessage requestMessage, final ByteBufAllocator allocator){
        return null;
    }

    @Override
    public ByteBuf serializeResponseAsBinary(ResponseMessage responseMessage, ByteBufAllocator allocator) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Optional<RequestMessage> deserializeRequest(ByteBuf msg) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Optional<ResponseMessage> deserializeResponse(ByteBuf msg) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String[] mimeTypesSupported() {
        return new String[]{"text/plain"};
    }
}

