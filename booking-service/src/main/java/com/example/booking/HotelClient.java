package com.example.booking;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import static com.example.booking.BookingDtos.*;
@Component
public class HotelClient {
 public static class SlotConflict extends RuntimeException {SlotConflict(){super("Room is occupied");}}
 public static class RemoteFailure extends RuntimeException {RemoteFailure(Throwable cause){super("Hotel service unavailable",cause);}}
 private final RestClient client;
 private final JwtTokens tokens;
 private final int retries;
 private void serviceHeaders(org.springframework.http.HttpHeaders headers){
  headers.setBearerAuth(tokens.service());
  String trace=org.slf4j.MDC.get("traceId");
  if(trace!=null)headers.set("X-Trace-Id",trace);
 }
 public HotelClient(@LoadBalanced RestClient.Builder builder,JwtTokens tokens,
  @Value("${hotel.service-url}") String serviceUrl,@Value("${hotel.retries:3}") int retries){
  this.client=builder.baseUrl(serviceUrl).build();this.tokens=tokens;this.retries=Math.max(1,retries);
 }
 @FunctionalInterface private interface Call<T>{T invoke();}
 private <T> T retry(Call<T> call){
  for(int attempt=1;attempt<=retries;attempt++){
   try{return call.invoke();}
   catch(RestClientResponseException ex){
    if(ex.getStatusCode().value()==409)throw new SlotConflict();
    if(ex.getStatusCode().is4xxClientError())throw new RemoteFailure(ex);
    if(attempt==retries)throw new RemoteFailure(ex);
   }catch(org.springframework.web.client.RestClientException ex){
    if(attempt==retries)throw new RemoteFailure(ex);
   }
   try{Thread.sleep(150L << (attempt-1));}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new RemoteFailure(ex);}
  }
  throw new IllegalStateException("Unreachable");
 }
 public List<RoomView> recommended(LocalDate start,LocalDate end){
  RoomView[] result=retry(()->client.get().uri(uri->uri.path("/api/rooms/recommend").queryParam("startDate",start).queryParam("endDate",end).build())
   .headers(this::serviceHeaders).retrieve().body(RoomView[].class));
  return result==null?List.of():List.of(result);
 }
 public void hold(Long roomId,Long bookingId,LocalDate start,LocalDate end){
  retry(()->{client.post().uri("/api/rooms/{id}/confirm-availability",roomId).headers(this::serviceHeaders)
   .body(new SlotRequest(bookingId,start,end)).retrieve().toBodilessEntity();return null;});
 }
 public void finalizeSlot(Long roomId,Long bookingId){
  retry(()->{client.post().uri("/api/rooms/{id}/finalize",roomId).headers(this::serviceHeaders)
   .body(new ReleaseRequest(bookingId,false)).retrieve().toBodilessEntity();return null;});
 }
 public void release(Long roomId,Long bookingId,boolean compensation){
  retry(()->{client.post().uri("/api/rooms/{id}/release",roomId).headers(this::serviceHeaders)
   .body(new ReleaseRequest(bookingId,compensation)).retrieve().toBodilessEntity();return null;});
 }
}
@Configuration
class HotelClientConfiguration {
 @Bean @LoadBalanced RestClient.Builder loadBalancedRestClientBuilder(
  @Value("${hotel.connect-timeout-ms:1000}") int connectTimeout,
  @Value("${hotel.read-timeout-ms:1500}") int readTimeout){
  var factory=new SimpleClientHttpRequestFactory();
  factory.setConnectTimeout(connectTimeout);factory.setReadTimeout(readTimeout);
  return RestClient.builder().requestFactory(factory);
 }
}
