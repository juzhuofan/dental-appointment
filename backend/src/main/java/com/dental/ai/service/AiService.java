package com.dental.ai.service;

import java.util.Map;
import java.util.List;
import com.dental.ai.dto.AiChatTurnDTO;

/** 其他业务模块只依赖此接口和自己的提示模板。 */
public interface AiService {

    String generate(String templateId, Map<String, Object> variables);

    <T> T generateStructured(String templateId, Map<String, Object> variables, Class<T> resultType);

    <T> T generateStructured(String templateId, Map<String, Object> variables,
            List<AiChatTurnDTO> history, Class<T> resultType);
}
