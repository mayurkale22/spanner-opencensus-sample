package com.sample.spanner;

/**
 * The names of properties which can be specified in the config files and flags.
 */
public final class CloudSpannerProperties {
  private CloudSpannerProperties() {}

  /**
   * The Cloud Spanner database name e.g. 'sample-database'.
   */
  static final String DATABASE = "cloudspanner.database";
  /**
   * The Cloud Spanner instance ID e.g. 'sample-instance'.
   */
  static final String INSTANCE = "cloudspanner.instance";
  /**
   * The Cloud Spanner database table e.g. 'person'.
   */
  static final String TABLE = "cloudspanner.table";
}