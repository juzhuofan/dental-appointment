package com.dental.ai.service;

import com.dental.ai.vo.AiReferenceVO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** 基于公开卫生资料的初步导诊。模型仅选择枚举和追问，不生成诊断、处方或剂量。 */
@Service
public class AiClinicalGuidanceService {

    private static final Pattern EMERGENCY = Pattern.compile(
            "呼吸困难|喘不过气|无法呼吸|不能呼吸|吞咽困难|无法吞咽|不能吞咽|说话困难|眼周肿胀|脖子肿|颈部肿胀");
    private static final Pattern NEGATED_OR_HYPOTHETICAL = Pattern.compile(
            "^无|没有|并无|无明显|不伴|未出现|没出现|不存在|不是|不觉得|否认|是否|如果|假如|会不会|担心会|假设");
    private static final Map<String, String> QUESTIONS;

    static {
        Map<String, String> questions = new LinkedHashMap<>();
        questions.put("PATIENT_AGE", "你是在为自己还是家人咨询？就诊人的年龄大约是多少？");
        questions.put("LOCATION", "不舒服的位置是牙齿、牙龈还是其他部位？具体在哪一侧？");
        questions.put("DURATION", "这种情况持续多久了，是持续出现还是间歇出现？");
        questions.put("PAIN_SEVERITY", "疼痛是否影响吃饭或睡觉，是否比之前加重？");
        questions.put("SWELLING", "是否有脸颊、牙龈或颈部肿胀？");
        questions.put("FEVER", "是否伴有发热？");
        questions.put("TRAUMA", "最近是否碰伤牙齿或发生其他口腔外伤？");
        questions.put("BREATHING", "是否出现呼吸、吞咽或说话困难？");
        QUESTIONS = Map.copyOf(questions);
    }

    public Map<String, Object> rules() {
        return Map.of("questions", QUESTIONS,
                "GENERAL", "症状信息不足时追问。口腔问题需要医生检查后判断原因。",
                "PROMPT_DENTIST", "牙痛持续超过两天，或伴发热、脸颊/下颌肿胀时应联系口腔医生评估。",
                "EMERGENCY", "呼吸/吞咽/说话困难，或口腔问题伴眼周、颈部肿胀时及时寻求急诊帮助。",
                "sources", references());
    }

    /** 明确的当前危险信号先由规则处理，即使模型不可用也可返回提示。 */
    public boolean hasImmediateRisk(String message) {
        Matcher matcher = EMERGENCY.matcher(message);
        while (matcher.find()) {
            String prefix = message.substring(Math.max(0, matcher.start() - 10), matcher.start());
            int boundary = Math.max(prefix.lastIndexOf('，'), Math.max(prefix.lastIndexOf('。'), prefix.lastIndexOf(',')));
            prefix = prefix.substring(boundary + 1);
            if (!NEGATED_OR_HYPOTHETICAL.matcher(prefix).find()
                    && !message.substring(matcher.end()).startsWith("吗")) {
                return true;
            }
        }
        return false;
    }

    public String advice(String level, List<String> questions) {
        if ("EMERGENCY".equals(level)) {
            return "如果你目前出现呼吸、吞咽或说话困难，或口腔问题伴眼周、颈部肿胀，请立即寻求急诊帮助，"
                    + "不要等待普通门诊号源或继续在线聊天。";
        }
        String advice = "PROMPT_DENTIST".equals(level)
                ? "根据你描述的情况，建议及时联系口腔医生评估，尤其是牙痛持续超过两天，或伴发热、脸颊/下颌肿胀时。"
                : "我可以帮你整理情况并选择就诊方向。仅凭文字描述不能判断原因，具体需要口腔医生检查。";
        List<String> selected = questions == null ? List.of() : questions;
        if (!selected.isEmpty()) {
            advice += "\n\n请再补充：\n" + selected.stream().map(QUESTIONS::get)
                    .filter(java.util.Objects::nonNull).map(question -> "• " + question)
                    .collect(java.util.stream.Collectors.joining("\n"));
        }
        return advice + "\n\n若出现呼吸、吞咽或说话困难等危险信号，请及时寻求急诊帮助。";
    }

    public boolean validQuestions(List<String> questions) {
        return questions == null || (questions.size() <= 3 && questions.stream().noneMatch(java.util.Objects::isNull)
                && QUESTIONS.keySet().containsAll(questions));
    }

    public List<AiReferenceVO> references() {
        return List.of(new AiReferenceVO("NHS：牙痛与就诊提示", "https://www.nhs.uk/symptoms/toothache/"),
                new AiReferenceVO("NHS：口腔感染相关急诊提示", "https://www.nhs.uk/conditions/dental-abscess/"));
    }
}
