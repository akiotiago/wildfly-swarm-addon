/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.jboss.forge.addon.swarm.facet;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.wildfly.swarm.tools.FractionDescriptor;
import org.wildfly.swarm.tools.FractionStability;
import org.wildfly.swarm.tools.PropertiesUtil;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

/**
 * @author Bob McWhirter
 */
// TODO: Remove when update to 2016.10
class FractionList implements org.wildfly.swarm.tools.FractionList
{

   private final Map<String, FractionDescriptor> descriptors = new TreeMap<>();

   private static final AtomicReference<FractionList> INSTANCE = new AtomicReference<>();

   public static FractionList get()
   {
      return INSTANCE.updateAndGet(old -> old != null ? old : new FractionList());
   }

   private FractionList()
   {
      try (InputStreamReader reader = new InputStreamReader(
               getClass().getClassLoader().getResourceAsStream("fraction-list.json")))
      {
         Json.parse(reader).asArray().forEach(entry -> {
            JsonObject fraction = entry.asObject();
            FractionDescriptor fd = getFractionDescriptor(fraction);
            addDependencies(fraction, fd);
         });
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }
   }

   private void addDependencies(JsonObject fraction, FractionDescriptor parent)
   {
      fraction.get("fractionDependencies").asArray().forEach(entry -> {
         JsonObject dependency = entry.asObject();
         FractionDescriptor descriptor = getFractionDescriptor(dependency);
         if (parent != null)
         {
            parent.addDependency(descriptor);
         }
         addDependencies(dependency, descriptor);
      });
   }

   private FractionDescriptor getFractionDescriptor(JsonObject fraction)
   {
      String groupId = toString(fraction.get("groupId"));
      String artifactId = toString(fraction.get("artifactId"));
      String key = groupId + ":" + artifactId;
      FractionDescriptor descriptor = descriptors.get(key);
      if (descriptor == null)
      {
         String version = toString(fraction.get("version"));
         String name = toString(fraction.get("name"));
         String description = toString(fraction.get("description"));
         String tags = toString(fraction.get("tags"));
         boolean internal = toBoolean(fraction.get("internal"));

         JsonValue stabilityIndexJson = fraction.get("stabilityIndex");
         int stabilityIndex = stabilityIndexJson == null || stabilityIndexJson.isNull()
                  ? FractionStability.UNSTABLE.ordinal() : stabilityIndexJson.asInt();
         FractionStability stability;
         if (stabilityIndex < 0 || stabilityIndex >= FractionStability.values().length)
         {
            stability = FractionStability.UNSTABLE;
         }
         else
         {
            stability = FractionStability.values()[stabilityIndex];
         }
         descriptor = new FractionDescriptor(groupId, artifactId, version, name, description, tags, internal,
                  stability);
         descriptors.put(key, descriptor);
      }
      return descriptor;
   }

   private boolean toBoolean(JsonValue jsonValue)
   {
      return jsonValue.isNull() ? false : jsonValue.asBoolean();
   }

   private String toString(JsonValue jsonValue)
   {
      return jsonValue.isNull() ? null : jsonValue.asString();
   }

   @Override
   public Collection<FractionDescriptor> getFractionDescriptors()
   {
      return Collections.unmodifiableCollection(this.descriptors.values());
   }

   @Override
   public FractionDescriptor getFractionDescriptor(final String groupId, final String artifactId)
   {
      return this.descriptors.get(groupId + ":" + artifactId);
   }

   @Override
   public Map<String, FractionDescriptor> getPackageSpecs()
   {
      final Map<String, String> packageSpecs = loadPackageSpecs();

      return this.descriptors.values().stream()
               .filter(fd -> packageSpecs.containsKey(fd.getArtifactId()))
               .collect(Collectors.toMap(fd -> packageSpecs.get(fd.getArtifactId()),
                        fd -> fd));
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private static Map<String, String> loadPackageSpecs()
   {
      try
      {
         final InputStream in = FractionList.class.getClassLoader()
                  .getResourceAsStream("org/wildfly/swarm/fractionlist/fraction-packages.properties");

         return new HashMap<>((Map) PropertiesUtil.loadProperties(in));
      }
      catch (IOException e)
      {
         throw new RuntimeException("Failed to load fraction-packages.properties", e);
      }

   }
}
