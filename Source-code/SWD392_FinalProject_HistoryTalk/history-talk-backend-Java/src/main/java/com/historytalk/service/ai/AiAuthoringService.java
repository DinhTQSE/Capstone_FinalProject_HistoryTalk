package com.historytalk.service.ai;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.historytalk.service.chat.AiServiceClient.AuthoringResponse;

/**
 * AI drafts of story scripts and battle-map plans for content admins and teachers.
 * Bodies follow the AI service contract; the context and character data are filled in here.
 */
public interface AiAuthoringService {

    AuthoringResponse generateStory(ObjectNode body);

    AuthoringResponse planBattleMap(ObjectNode body);

    AuthoringResponse locateAnchors(ObjectNode body);

    AuthoringResponse getJob(String jobId);

    AuthoringResponse cancelJob(String jobId);
}
