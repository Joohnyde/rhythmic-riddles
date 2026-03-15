package com.cevapinxile.cestereg.api.support;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.springframework.test.web.servlet.ResultActions;

public abstract class AdditionalControllerTestSupport extends ControllerTestSupport {

  protected void expectJsonUtf8(ResultActions actions) throws Exception {
    actions.andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
  }

  protected void expectCorsWildcard(ResultActions actions) throws Exception {
    actions.andExpect(header().string("Access-Control-Allow-Origin", "*"));
  }

  protected void expectNoServiceCalls(Object... mocks) {
    for (Object mock : mocks) {
      verifyNoInteractions(mock);
    }
  }
}
