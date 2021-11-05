/*
 *  Copyright (C) 2021 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package we.plugin.apidoc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import we.api.pairing.ApiPairingDocSetService;
import we.config.SystemConfig;
import we.plugin.FizzPluginFilter;
import we.plugin.FizzPluginFilterChain;
import we.util.ReactorUtils;
import we.util.WebUtils;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 
 * @author Francis Dong
 *
 */
@ConditionalOnProperty(name = SystemConfig.FIZZ_API_PAIRING_SERVER_ENABLE, havingValue = "true")
@Component(ApiDocAuthPluginFilter.API_DOC_AUTH_PLUGIN_FILTER)
public class ApiDocAuthPluginFilter implements FizzPluginFilter {

	private static final Logger log = LoggerFactory.getLogger(ApiDocAuthPluginFilter.class);

	public static final String API_DOC_AUTH_PLUGIN_FILTER = "apiDocAuthPlugin";

	@Resource
	private ApiPairingDocSetService apiPairingDocSetService;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, Map<String, Object> config) {
		String traceId = WebUtils.getTraceId(exchange);
		try {
			String appId = WebUtils.getAppId(exchange);
			String service = WebUtils.getClientService(exchange);
			String path = WebUtils.getClientReqPath(exchange);
			HttpMethod method = exchange.getRequest().getMethod();
			if (apiPairingDocSetService.existsDocSetMatch(appId, method, service, path)) {
				// Go to next plugin
				Mono next = FizzPluginFilterChain.next(exchange);
				return next.defaultIfEmpty(ReactorUtils.NULL).flatMap(nil -> {
					doAfter();
					return Mono.empty();
				});
			} else {
				// Auth failed
				ServerHttpResponse response = exchange.getResponse();
				response.setStatusCode(HttpStatus.UNAUTHORIZED);
				response.getHeaders().setCacheControl("no-store");
				response.getHeaders().setExpires(0);
				String respJson = WebUtils.jsonRespBody(HttpStatus.UNAUTHORIZED.value(),
						HttpStatus.UNAUTHORIZED.getReasonPhrase(), traceId);
				return WebUtils.response(exchange, HttpStatus.UNAUTHORIZED, null, respJson);
			}
		} catch (Exception e) {
			log.error("{} {} exception", traceId, API_DOC_AUTH_PLUGIN_FILTER, e);
			String respJson = WebUtils.jsonRespBody(HttpStatus.INTERNAL_SERVER_ERROR.value(),
					HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), traceId);
			return WebUtils.response(exchange, HttpStatus.INTERNAL_SERVER_ERROR, null, respJson);
		}
	}

	public void doAfter() {

	}

}
