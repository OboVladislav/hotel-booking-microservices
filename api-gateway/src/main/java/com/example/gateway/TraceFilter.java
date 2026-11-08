package com.example.gateway;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
@Component
public class TraceFilter implements GlobalFilter, Ordered {
 private static final Logger log=LoggerFactory.getLogger(TraceFilter.class);
 @Override public int getOrder(){return -100;}
 @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
  String trace=exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
  if(trace==null || trace.isBlank()) trace=UUID.randomUUID().toString();
  String id=trace;
  var request=exchange.getRequest().mutate().headers(h->h.set("X-Trace-Id",id)).build();
  exchange.getResponse().getHeaders().set("X-Trace-Id",id);
  log.info("traceId={} method={} path={}",id,request.getMethod(),request.getURI().getPath());
  return chain.filter(exchange.mutate().request(request).build());
 }
}
