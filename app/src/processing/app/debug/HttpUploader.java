package processing.app.debug;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import processing.app.Base;
import processing.app.Preferences;
import processing.app.SerialException;
import processing.app.helpers.PreferencesMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpUploader extends Uploader {

  private static final Pattern IPV4_ADDRESS = Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})");

  private final HttpClient client;
  private final String ipAddress;

  public HttpUploader(String port) {
    this.client = new HttpClient();
    this.client.getHttpConnectionManager().getParams().setConnectionTimeout(5000);
    Matcher matcher = IPV4_ADDRESS.matcher(port);
    if (!matcher.find()) {
      throw new IllegalArgumentException(port);
    }
    this.ipAddress = matcher.group(1);
  }

  public boolean requiresAuthorization() {
    return true;
  }

  public String getAuthorizationKey() {
    return "pwd." + ipAddress;
  }

  @Override
  public boolean uploadUsingPreferences(String buildPath, String className, boolean usingProgrammer) throws RunnerException, SerialException {
    if (usingProgrammer) {
      System.err.println("Http upload using programmer not supported");
      return false;
    }

    FilePart sketch;
    try {
      sketch = new FilePart("sketch", new File(buildPath, className + ".hex"));
    } catch (FileNotFoundException e) {
      throw new RunnerException(e);
    }

    TargetPlatform targetPlatform = Base.getTargetPlatform();
    PreferencesMap prefs = Preferences.getMap();
    prefs.putAll(Base.getBoardPreferences());
    prefs.putAll(targetPlatform.getTool(prefs.get("upload.tool")));
    boolean verbose = prefs.containsKey("upload.verbose") && Boolean.parseBoolean(prefs.get("upload.verbose"));

    StringPart params = new StringPart("params", verbose ? prefs.get("upload.params.verbose") : prefs.get("upload.params.quiet"));

    Part[] parts = {sketch, params};
    PostMethod post = newPostMethod();
    post.setRequestHeader("Cookie", "pwd=" + Preferences.get(getAuthorizationKey()));
    post.setRequestEntity(new MultipartRequestEntity(parts, post.getParams()));

    int statusCode;
    try {
      statusCode = client.executeMethod(post);

      System.err.println(post.getResponseBodyAsString());
      return statusCode == HttpStatus.SC_OK;
    } catch (IOException e) {
      throw new RunnerException(e);
    } finally {
      post.releaseConnection();
    }
  }

  protected PostMethod newPostMethod() {
    return new PostMethod("http://" + ipAddress + "/upload");
  }

  @Override
  public boolean burnBootloader() throws RunnerException {
    throw new RunnerException("Can't burn bootloader via http");
  }

}