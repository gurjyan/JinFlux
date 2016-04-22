package org.jlab.coda.jinflux;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Class description here....
 * <p>
 *
 * @author gurjyan
 *         Date 4/19/16
 * @version 3.x
 */
public class JinFlux {
    private InfluxDB influxDB;

    public JinFlux(String influxDbHost, String user, String password) {
        this.influxDB = InfluxDBFactory.connect("http://" + influxDbHost + ":8086", user, password);

        // Flush every 1000 points, at least every 100ms (which one comes first)
        this.influxDB.enableBatch(1000, 100, TimeUnit.MILLISECONDS);
    }

    public JinFlux(String influxDbHost) {
        this(influxDbHost, "root", "root");
    }

    public boolean ping(int timeout) throws Exception {
        int tries = 0;
        timeout = timeout * 10;
        boolean influxStarted = false;
        do {
            Pong response;
            response = this.influxDB.ping();
            if (!response.getVersion().equalsIgnoreCase("unknown")) {
                influxStarted = true;
            }
            Thread.sleep(100L);
            tries++;
        } while (!influxStarted || (tries < timeout));

        return (tries < timeout);
    }

    public boolean dbCreate(String dbName) {
        influxDB.createDatabase(dbName);
        return doesDbExists(dbName);
    }

    public void dbRemove(String dbName) {
        influxDB.deleteDatabase(dbName);
    }

    public List<String> dbList() {
        return influxDB.describeDatabases();
    }

    public boolean doesDbExists(String dbName) {
        boolean exists = false;
        List<String> result = dbList();
        if (result != null && result.size() > 0) {
            for (String database : result) {
                if (database.equals(dbName)) {
                    exists = true;
                    break;
                }
            }
        }
        return exists;
    }

    private Point.Builder addFieldValue(Point.Builder point,
                                        String field,
                                        Object value) {

        if (value instanceof String) {
            point.addField(field, (String) value);

        } else if (value instanceof Boolean) {
            point.addField(field, (Boolean) value);

        } else if (value instanceof Long) {
            point.addField(field, (Long) value);

        } else if (value instanceof Double) {
            point.addField(field, (Double) value);

        } else if (value instanceof Number) {
            point.addField(field, (Number) value);
        }
        return point;
    }

    private Point.Builder addFieldValue(Point.Builder point,
                                        Map<String, Object> tags){

        for (String tag : tags.keySet()) {
                addFieldValue(point, tag, tags.get(tag));
        }
        return point;
    }


    public Point.Builder eventSpot(String measurement) {

        return Point.measurement(measurement);

    }

    public Point.Builder eventSpot(String measurement,
                               String tagName,
                               String value) {

       return Point.measurement(measurement).tag(tagName,value);
    }

    public Point.Builder eventSpot(String measurement,
                               Map<String, String> tags) {

        return Point.measurement(measurement).tag(tags);

    }


    public void write(String dbName,
                      Point.Builder spot,
                      String fieldName,
                      Object fieldValue) {

               Point.Builder p = addFieldValue(spot, fieldName, fieldValue)
                .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS);

        // write the point to the database
        influxDB.write(dbName, "default", p.build());
    }

    public void write(String dbName,
                      Point.Builder spot,
                      String fieldName,
                      List<Object> fieldValues) {

               Point.Builder p = addFieldValue(spot, fieldName, fieldValues)
                .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS);

        // write the point to the database
        influxDB.write(dbName, "default", p.build());
    }

    public void write(String dbName,
                      Point.Builder spot,
                      Map<String, Object> fields) {

        Point.Builder p = addFieldValue(spot, fields)
                .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS);

        // write the point to the database
        influxDB.write(dbName, "default", p.build());
    }


    public Map<Object, Object> read(String dbName, String measurement, String tag) throws JinFluxException {
        if (tag.equals("*")) throw new JinFluxException("wildcards are not supported");
        Map<Object, Object> rm = new LinkedHashMap<>();
        Object o1 = null;
        Object o2 = null;
        Query query = new Query("SELECT " + tag + " FROM " + measurement, dbName);
        QueryResult r = influxDB.query(query);
        if (r != null) {
            for (QueryResult.Result qr : r.getResults()) {
                for (QueryResult.Series sr : qr.getSeries()) {
                    for (List<Object> l : sr.getValues()) {
                        boolean first = true;
                        for (Object ll : l) {
                            if (first) {
                                o1 = ll;
                                first = false;
                            } else {
                                o2 = ll;
                                first = true;
                            }
                            if (first) {
                                rm.put(o1, o2);
                            }
                        }
                    }
                }
            }
        }
        return rm;
    }

    public List<String> readTags(String dbName, String measurement) {
        List<String> rl = new ArrayList<>();
        Query query = new Query("SELECT *  FROM " + measurement, dbName);
        QueryResult r = influxDB.query(query);
        for (QueryResult.Result qr : r.getResults()) {
            for (QueryResult.Series sr : qr.getSeries()) {
                List<String> l = sr.getColumns();
                rl.addAll(l);
            }
        }
        return rl;
    }

    /**
     * Queries the database. excepts "*" wildcard.
     *
     * @param dbName
     * @param measurement
     * @param tag
     */
    public void dump(String dbName, String measurement, String tag) {
        Query query = new Query("SELECT " + tag + " FROM " + measurement, dbName);
        QueryResult r = influxDB.query(query);
        for (QueryResult.Result qr : r.getResults()) {
            for (QueryResult.Series sr : qr.getSeries()) {
                System.out.println("===================================================");
                System.out.println("              " + sr.getName());
                System.out.println("---------------------------------------------------");

                for (String column : sr.getColumns()) {
                    if (column.equals("time"))
                        System.out.print(column + "                        ");
                    else System.out.print(column + "\t");
                }
                System.out.println();
                System.out.println("---------------------------------------------------");
                for (List<Object> l : sr.getValues()) {
                    boolean first = true;
                    for (Object ll : l) {
                        if (first) {
                            System.out.print(ll + "\t");
                            first = false;
                        } else {
                            System.out.print(ll + "\t\t");
                        }
                    }
                    System.out.println();
                }
                System.out.println("===================================================");
            }
        }
    }

}
