package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.community.dto.CreateSurveyRequest;
import com.cloudmart.community.dto.SurveyResponseRequest;
import com.cloudmart.community.entity.CommunitySurvey;
import com.cloudmart.community.entity.CommunitySurveyAnswer;
import com.cloudmart.community.entity.CommunitySurveyQuestion;
import com.cloudmart.community.repository.CommunitySurveyAnswerMapper;
import com.cloudmart.community.repository.CommunitySurveyMapper;
import com.cloudmart.community.repository.CommunitySurveyQuestionMapper;
import com.cloudmart.community.service.SurveyService;
import com.cloudmart.community.vo.SurveyVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 问卷服务实现（编辑器附件，V10）。
 *
 * <p>关键设计：客户端 UUID 为主键，发布链路幂等；选项文本/选项 ID 序列化为
 * JSON 字符串存储（TEXT 列），答案以 uk(survey, question, user) upsert 支持改答案。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SurveyServiceImpl implements SurveyService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {
    };

    private final CommunitySurveyMapper surveyMapper;
    private final CommunitySurveyQuestionMapper questionMapper;
    private final CommunitySurveyAnswerMapper answerMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurveyVO createSurvey(Long userId, CreateSurveyRequest request) {
        CommunitySurvey existing = surveyMapper.selectById(request.id());
        if (existing != null) {
            if (!existing.getCreatorId().equals(userId)) {
                throw new BusinessException("FORBIDDEN", "该问卷 ID 已被使用");
            }
            return getSurvey(existing.getId(), userId);
        }

        CommunitySurvey survey = new CommunitySurvey();
        survey.setId(request.id());
        survey.setTargetType(request.targetType());
        survey.setTargetId(request.targetId());
        survey.setCreatorId(userId);
        survey.setTitle(request.title().trim());
        surveyMapper.insert(survey);

        List<CreateSurveyRequest.SurveyQuestionInput> inputs = request.questions();
        for (int index = 0; index < inputs.size(); index++) {
            CreateSurveyRequest.SurveyQuestionInput input = inputs.get(index);
            boolean textType = "text".equals(input.type());
            List<String> options = textType
                    ? List.of()
                    : normalizeOptions(input.options());
            if (!textType && options.size() < 2) {
                throw new BusinessException("SURVEY_QUESTION_INVALID", "第 " + (index + 1) + " 题至少需要 2 个选项");
            }
            CommunitySurveyQuestion question = new CommunitySurveyQuestion();
            question.setSurveyId(survey.getId());
            question.setContent(input.text().trim());
            question.setType(input.type().toUpperCase());
            question.setOptions(writeJson(options));
            question.setIsRequired(!Boolean.FALSE.equals(input.required()));
            question.setSort(index);
            questionMapper.insert(question);
        }
        log.info("问卷已创建: surveyId={}, userId={}, targetType={}, targetId={}",
                survey.getId(), userId, survey.getTargetType(), survey.getTargetId());
        return getSurvey(survey.getId(), userId);
    }

    @Override
    public SurveyVO getSurvey(String surveyId, Long userId) {
        CommunitySurvey survey = surveyMapper.selectById(surveyId);
        if (survey == null) {
            throw new BusinessException("SURVEY_NOT_FOUND", "问卷不存在");
        }
        List<CommunitySurveyQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<CommunitySurveyQuestion>()
                        .eq(CommunitySurveyQuestion::getSurveyId, surveyId)
                        .orderByAsc(CommunitySurveyQuestion::getSort)
                        .orderByAsc(CommunitySurveyQuestion::getId));
        List<CommunitySurveyAnswer> answers = answerMapper.selectList(
                new LambdaQueryWrapper<CommunitySurveyAnswer>()
                        .eq(CommunitySurveyAnswer::getSurveyId, surveyId));
        long respondentCount = answers.stream().map(CommunitySurveyAnswer::getUserId).distinct().count();

        Map<Long, List<CommunitySurveyAnswer>> answersByQuestion = answers.stream()
                .collect(Collectors.groupingBy(CommunitySurveyAnswer::getQuestionId));

        Map<Long, CommunitySurveyAnswer> myAnswersByQuestion = userId == null
                ? Map.of()
                : answers.stream()
                        .filter(answer -> answer.getUserId().equals(userId))
                        .collect(Collectors.toMap(CommunitySurveyAnswer::getQuestionId, answer -> answer,
                                (first, second) -> second));

        List<SurveyVO.SurveyQuestionVO> questionVOs = questions.stream().map(question -> {
            List<String> options = readJson(question.getOptions(), STRING_LIST_TYPE);
            List<CommunitySurveyAnswer> questionAnswers =
                    answersByQuestion.getOrDefault(question.getId(), List.of());
            // 选择题：选项文本存题目 JSON（无独立行），答案记录选项下标（0 基）——票数按下标对齐输出
            if ("SINGLE".equals(question.getType()) || "MULTI".equals(question.getType())) {
                Map<Integer, Long> countsByIndex = new HashMap<>();
                for (CommunitySurveyAnswer answer : questionAnswers) {
                    for (Long optionIndex : readJson(answer.getOptionIds(), LONG_LIST_TYPE)) {
                        countsByIndex.merge(optionIndex.intValue(), 1L, Long::sum);
                    }
                }
                List<Long> optionCounts = new ArrayList<>(options.size());
                for (int index = 0; index < options.size(); index++) {
                    optionCounts.add(countsByIndex.getOrDefault(index, 0L));
                }
                return new SurveyVO.SurveyQuestionVO(question.getId(), question.getContent(), question.getType(),
                        options, question.getIsRequired(), optionCounts, (long) questionAnswers.size());
            }
            long textAnswerCount = questionAnswers.stream()
                    .filter(answer -> answer.getTextContent() != null && !answer.getTextContent().isBlank())
                    .count();
            return new SurveyVO.SurveyQuestionVO(question.getId(), question.getContent(), question.getType(),
                    options, question.getIsRequired(), List.of(), textAnswerCount);
        }).toList();

        List<SurveyVO.MyAnswerVO> myAnswers = myAnswersByQuestion.entrySet().stream()
                .map(entry -> new SurveyVO.MyAnswerVO(
                        entry.getKey(),
                        readJson(entry.getValue().getOptionIds(), LONG_LIST_TYPE),
                        entry.getValue().getTextContent()))
                .sorted(Comparator.comparing(SurveyVO.MyAnswerVO::questionId))
                .toList();

        return new SurveyVO(survey.getId(), survey.getTitle(), questionVOs, myAnswers, respondentCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitResponse(String surveyId, Long userId, SurveyResponseRequest request) {
        CommunitySurvey survey = surveyMapper.selectById(surveyId);
        if (survey == null) {
            throw new BusinessException("SURVEY_NOT_FOUND", "问卷不存在");
        }
        List<CommunitySurveyQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<CommunitySurveyQuestion>()
                        .eq(CommunitySurveyQuestion::getSurveyId, surveyId));
        Map<Long, CommunitySurveyQuestion> questionById = questions.stream()
                .collect(Collectors.toMap(CommunitySurveyQuestion::getId, question -> question));

        Set<Long> answeredQuestionIds = new HashSet<>();
        for (SurveyResponseRequest.SurveyAnswerInput answer : request.answers()) {
            CommunitySurveyQuestion question = questionById.get(answer.questionId());
            if (question == null) {
                throw new BusinessException("SURVEY_QUESTION_INVALID", "存在不属于该问卷的题目");
            }
            if (answeredQuestionIds.contains(answer.questionId())) {
                throw new BusinessException("SURVEY_QUESTION_INVALID", "同一题目重复作答");
            }
            answeredQuestionIds.add(answer.questionId());

            String optionIdsJson = null;
            String textContent = null;
            if ("TEXT".equals(question.getType())) {
                textContent = answer.text() == null ? "" : answer.text().trim();
                if (Boolean.TRUE.equals(question.getIsRequired()) && textContent.isEmpty()) {
                    throw new BusinessException("SURVEY_QUESTION_REQUIRED", "「" + question.getContent() + "」为必答题");
                }
            } else {
                List<Long> optionIds = answer.optionIds() == null
                        ? List.of()
                        : List.copyOf(new HashSet<>(answer.optionIds()));
                if (Boolean.TRUE.equals(question.getIsRequired()) && optionIds.isEmpty()) {
                    throw new BusinessException("SURVEY_QUESTION_REQUIRED", "「" + question.getContent() + "」为必答题");
                }
                if ("SINGLE".equals(question.getType()) && optionIds.size() > 1) {
                    throw new BusinessException("SURVEY_QUESTION_INVALID", "单选题只能选择 1 个选项");
                }
                List<String> options = readJson(question.getOptions(), STRING_LIST_TYPE);
                if (optionIds.stream().anyMatch(optionId -> optionId < 0 || optionId >= options.size())) {
                    throw new BusinessException("SURVEY_QUESTION_INVALID", "选项序号非法");
                }
                optionIdsJson = writeJson(optionIds);
            }

            CommunitySurveyAnswer existing = answerMapper.selectOne(new LambdaQueryWrapper<CommunitySurveyAnswer>()
                    .eq(CommunitySurveyAnswer::getSurveyId, surveyId)
                    .eq(CommunitySurveyAnswer::getQuestionId, answer.questionId())
                    .eq(CommunitySurveyAnswer::getUserId, userId)
                    .last("LIMIT 1"));
            if (existing == null) {
                CommunitySurveyAnswer entity = new CommunitySurveyAnswer();
                entity.setSurveyId(surveyId);
                entity.setQuestionId(answer.questionId());
                entity.setUserId(userId);
                entity.setOptionIds(optionIdsJson);
                entity.setTextContent(textContent);
                answerMapper.insert(entity);
            } else {
                existing.setOptionIds(optionIdsJson);
                existing.setTextContent(textContent);
                answerMapper.updateById(existing);
            }
        }

        // 必答题未覆盖校验（漏答）
        for (CommunitySurveyQuestion question : questions) {
            if (Boolean.TRUE.equals(question.getIsRequired()) && !answeredQuestionIds.contains(question.getId())) {
                throw new BusinessException("SURVEY_QUESTION_REQUIRED", "「" + question.getContent() + "」为必答题");
            }
        }
        log.info("问卷答卷提交: surveyId={}, userId={}, answers={}", surveyId, userId, answeredQuestionIds.size());
    }

    private List<String> normalizeOptions(List<String> rawOptions) {
        if (rawOptions == null) {
            return List.of();
        }
        return rawOptions.stream().map(String::trim).filter(option -> !option.isEmpty()).toList();
    }

    private String writeJson(List<?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("INTERNAL_ERROR", "问卷选项序列化失败");
        }
    }

    private <T> List<T> readJson(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<T> parsed = objectMapper.readValue(json, type);
            return parsed == null ? List.of() : parsed;
        } catch (JsonProcessingException e) {
            log.warn("问卷 JSON 解析失败: {}", json);
            return List.of();
        }
    }
}
