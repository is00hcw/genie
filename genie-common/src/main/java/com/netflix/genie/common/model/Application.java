/*
 *
 *  Copyright 2014 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.genie.common.model;

import com.netflix.genie.common.exceptions.GenieException;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;
import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.Set;
import javax.persistence.Basic;
import javax.persistence.Cacheable;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Representation of the state of Application Configuration object.
 *
 * @author amsharma
 * @author tgianos
 */
@Entity
@Cacheable(false)
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(value = "An Application")
public class Application extends CommonEntityFields {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    /**
     * If it is in use - ACTIVE, DEPRECATED, INACTIVE.
     */
    @Basic(optional = false)
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(
            value = "The current status of this application",
            required = true)
    private ApplicationStatus status = ApplicationStatus.INACTIVE;

    /**
     * Users can specify a property file location with environment variables.
     */
    @Basic
    @ApiModelProperty(
            value = "Users can specify a property file location with environment variables")
    private String envPropFile;

    /**
     * Reference to all the configurations needed for this application.
     */
    @XmlElementWrapper(name = "configs")
    @XmlElement(name = "config")
    @ElementCollection(fetch = FetchType.EAGER)
    @ApiModelProperty(
            value = "All the configuration files needed for this application")
    private Set<String> configs;

    /**
     * Set of jars required for this application.
     */
    @XmlElementWrapper(name = "jars")
    @XmlElement(name = "jar")
    @ElementCollection(fetch = FetchType.EAGER)
    @ApiModelProperty(
            value = "Any jars needed to run this application")
    private Set<String> jars;

    /**
     * The commands this application is associated with.
     */
    @XmlTransient
    @JsonIgnore
    @OneToMany(mappedBy = "application", fetch = FetchType.LAZY)
    private Set<Command> commands;

    /**
     * Set of tags for a application.
     */
    @XmlElementWrapper(name = "tags")
    @XmlElement(name = "tag")
    @ElementCollection(fetch = FetchType.EAGER)
    @ApiModelProperty(
            value = "Reference to all the tags"
            + " associated with this application.")
    private Set<String> tags;

    /**
     * Default constructor.
     */
    public Application() {
        super();
    }

    /**
     * Construct a new Application with all required parameters.
     *
     * @param name The name of the application. Not null/empty/blank.
     * @param user The user who created the application. Not null/empty/blank.
     * @param status The status of the application. Not null.
     * @param version The version of this application
     */
    public Application(
            final String name,
            final String user,
            final ApplicationStatus status,
            final String version) {
        super(name, user, version);
        this.status = status;
    }

    /**
     * Check to make sure everything is OK before persisting.
     *
     * @throws GenieException
     */
    @PrePersist
    @PreUpdate
    protected void onCreateOrUpdateApplication() throws GenieException {
        this.validate(this.status);
        // Add the id to the tags
        if (this.tags == null) {
            this.tags = new HashSet<String>();
        }
        this.tags.add(this.getId());
    }

    /**
     * Gets the status for this application.
     *
     * @return status
     * @see ApplicationStatus
     */
    public ApplicationStatus getStatus() {
        return status;
    }

    /**
     * Sets the status for this application.
     *
     * @param status One of the possible statuses
     */
    public void setStatus(final ApplicationStatus status) {
        this.status = status;
    }

    /**
     * Gets the envPropFile name.
     *
     * @return envPropFile - file name containing environment variables.
     */
    public String getEnvPropFile() {
        return envPropFile;
    }

    /**
     * Sets the env property file name in string form.
     *
     * @param envPropFile contains the list of env variables to set while
     * running a command using this application.
     */
    public void setEnvPropFile(final String envPropFile) {
        this.envPropFile = envPropFile;
    }

    /**
     * Gets the configurations for this application.
     *
     * @return the configurations for this application
     */
    public Set<String> getConfigs() {
        return this.configs;
    }

    /**
     * Sets the configurations for this application.
     *
     * @param configs The configuration files that this application needs
     */
    public void setConfigs(final Set<String> configs) {
        this.configs = configs;
    }

    /**
     * Gets the jars for this application.
     *
     * @return list of jars this application relies on for execution
     */
    public Set<String> getJars() {
        return this.jars;
    }

    /**
     * Sets the jars needed for this application.
     *
     * @param jars All jars needed for execution of this application
     */
    public void setJars(final Set<String> jars) {
        this.jars = jars;
    }

    /**
     * Get all the commands associated with this application.
     *
     * @return The commands
     */
    public Set<Command> getCommands() {
        return this.commands;
    }

    /**
     * Set all the commands associated with this application.
     *
     * @param commands The commands to set.
     */
    protected void setCommands(final Set<Command> commands) {
        this.commands = commands;
    }

    /**
     * Gets the tags allocated to this application.
     *
     * @return the tags as an unmodifiable list
     */
    public Set<String> getTags() {
        return this.tags;
    }

    /**
     * Sets the tags allocated to this application.
     *
     * @param tags the tags to set.
     */
    public void setTags(final Set<String> tags) {
        this.tags = tags;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validate() throws GenieException {
        super.validate();
        this.validate(this.getStatus());
    }

    /**
     * Helper method for checking the validity of required parameters.
     *
     * @param status The status of the application
     * @throws GenieException
     */
    private void validate(
            final ApplicationStatus status) throws GenieException {
        final StringBuilder builder = new StringBuilder();
        if (status == null) {
            builder.append("No application status entered and is required.\n");
        }

        if (builder.length() > 0) {
            builder.insert(0, "Application configuration errors:\n");
            final String msg = builder.toString();
            LOG.error(msg);
            throw new GenieException(HttpURLConnection.HTTP_BAD_REQUEST, msg);
        }
    }
}