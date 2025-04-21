/*
 * Copyright (c) 2024-2025 Stefan Toengi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.schnippsche.solarreader.plugins.emlog;

import de.schnippsche.solarreader.backend.calculator.MapCalculator;
import de.schnippsche.solarreader.backend.connection.general.ConnectionFactory;
import de.schnippsche.solarreader.backend.connection.network.HttpConnection;
import de.schnippsche.solarreader.backend.connection.network.HttpConnectionFactory;
import de.schnippsche.solarreader.backend.protocol.KnownProtocol;
import de.schnippsche.solarreader.backend.provider.AbstractHttpProvider;
import de.schnippsche.solarreader.backend.provider.CommandProviderProperty;
import de.schnippsche.solarreader.backend.provider.ProviderProperty;
import de.schnippsche.solarreader.backend.provider.SupportedInterface;
import de.schnippsche.solarreader.backend.singleton.GlobalUsrStore;
import de.schnippsche.solarreader.backend.table.Table;
import de.schnippsche.solarreader.backend.util.JsonTools;
import de.schnippsche.solarreader.backend.util.Setting;
import de.schnippsche.solarreader.backend.util.StringConverter;
import de.schnippsche.solarreader.backend.util.TimeEvent;
import de.schnippsche.solarreader.database.Activity;
import de.schnippsche.solarreader.frontend.ui.UIList;
import de.schnippsche.solarreader.plugin.PluginMetadata;
import java.io.IOException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import org.tinylog.Logger;

/**
 * Represents a specific HTTP provider implementation for Emlog systems. This class extends {@link
 * AbstractHttpProvider} and provides functionality for interacting with Emlog systems using HTTP
 * protocols.
 *
 * <p>The {@link Emlog} class is responsible for managing HTTP communication with Emlog systems,
 * including sending requests, receiving responses, and handling data exchange.
 */
@PluginMetadata(
    name = "Emlog",
    version = "1.0.1",
    author = "Stefan Töngi",
    url = "https://github.com/solarreader-plugins/plugin-Emlog",
    svgImage = "emlog.svg",
    supportedInterfaces = {SupportedInterface.NONE},
    usedProtocol = KnownProtocol.HTTP,
    supports = "")
public class Emlog extends AbstractHttpProvider {
  private static final DateTimeFormatter todayFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final String BASE_URL =
      "http://emlog/pages/getinformation.php?heute&datum={today}&meterindex=1";
  private static final String EMLOG_CONSTANT = "emlog";

  /**
   * Constructs a new {@link Emlog} instance using a default {@link HttpConnectionFactory}.
   *
   * <p>This constructor initializes the {@link Emlog} instance with a default HTTP connection
   * factory and invokes the parent class constructor.
   *
   * @see HttpConnectionFactory
   */
  public Emlog() {
    this(new HttpConnectionFactory());
  }

  /**
   * Constructs a new {@link Emlog} instance using the specified {@link ConnectionFactory}.
   *
   * <p>This constructor allows custom connection factory to be provided. It initializes the {@link
   * Emlog} instance with the specified factory and invokes the parent class constructor.
   *
   * @param connectionFactory the {@link ConnectionFactory} to create the HTTP connection
   * @see ConnectionFactory
   * @see HttpConnection
   */
  public Emlog(ConnectionFactory<HttpConnection> connectionFactory) {
    super(connectionFactory);
    Logger.debug("instantiate {}", this.getClass().getName());
  }

  @Override
  public ResourceBundle getPluginResourceBundle() {
    return ResourceBundle.getBundle(EMLOG_CONSTANT, locale);
  }

  @Override
  public Activity getDefaultActivity() {
    return new Activity(TimeEvent.TIME, 0, TimeEvent.TIME, 86399, 20, TimeUnit.SECONDS);
  }

  @Override
  public Optional<UIList> getProviderDialog() {
    return Optional.empty();
  }

  @Override
  public Optional<List<ProviderProperty>> getSupportedProperties() {
    return getSupportedPropertiesFromFile("emlog_fields.json");
  }

  @Override
  public Optional<List<Table>> getDefaultTables() {
    return getDefaultTablesFromFile("emlog_tables.json");
  }

  @Override
  public Setting getDefaultProviderSetting() {
    Setting setting = new Setting();
    setting.setProviderHost(EMLOG_CONSTANT);
    return setting;
  }

  @Override
  public String testProviderConnection(Setting testSetting)
      throws IOException, InterruptedException {
    HttpConnection connection = connectionFactory.createConnection(testSetting);
    URL testUrl = getApiUrl(BASE_URL);
    connection.test(testUrl, HttpConnection.CONTENT_TYPE_JSON);
    return resourceBundle.getString("emlog.connection.successful");
  }

  @Override
  public void doOnFirstRun() {
    doStandardFirstRun();
  }

  @Override
  public boolean doActivityWork(Map<String, Object> variables)
      throws IOException, InterruptedException {
    workProperties(getConnection(), variables);
    return true;
  }

  @Override
  public String getLockObject() {
    return EMLOG_CONSTANT;
  }

  private URL getApiUrl(String urlPattern) throws IOException {
    Map<String, String> configurationValues = new HashMap<>();
    ZonedDateTime current = GlobalUsrStore.getInstance().getCurrentZonedDateTime();
    configurationValues.put("today", current.format(todayFormatter));
    String urlString =
        new StringConverter(urlPattern).replaceNamedPlaceholders(configurationValues);
    Logger.debug("url:{}", urlString);
    return new StringConverter(urlString).toUrl();
  }

  @Override
  protected void handleCommandProperty(
      HttpConnection httpConnection,
      CommandProviderProperty commandProviderProperty,
      Map<String, Object> variables)
      throws IOException, InterruptedException {
    String pattern = commandProviderProperty.getCommand();
    URL url = getApiUrl(pattern);
    Map<String, Object> values =
        new JsonTools().getSimpleMapFromJsonString(httpConnection.getAsString(url));
    new MapCalculator()
        .calculate(values, commandProviderProperty.getPropertyFieldList(), variables);
  }
}
