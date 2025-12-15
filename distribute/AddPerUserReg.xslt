<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:wix="http://schemas.microsoft.com/wix/2006/wi"
                xmlns="http://schemas.microsoft.com/wix/2006/wi"
                exclude-result-prefixes="wix">

    <xsl:output method="xml" indent="yes" omit-xml-declaration="yes" />

    <xsl:key name="DirById" match="wix:Directory" use="@Id" />

    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="wix:Component">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>

            <RegistryValue Root="HKCU" Key="Software\[Manufacturer]\Maestro\Install" Name="{@Id}" Type="string" Value="1" KeyPath="yes" />

            <xsl:call-template name="GenerateRemoveFolder">
                <xsl:with-param name="DirId" select="@Directory" />
                <xsl:with-param name="CompId" select="@Id" />
                <xsl:with-param name="Level" select="0" />
            </xsl:call-template>

            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template name="GenerateRemoveFolder">
        <xsl:param name="DirId" />
        <xsl:param name="CompId" />
        <xsl:param name="Level" />

        <RemoveFolder Id="Rm_{$CompId}_{$Level}" Directory="{$DirId}" On="uninstall" />

        <xsl:variable name="DirNode" select="key('DirById', $DirId)" />

        <xsl:variable name="ParentId">
            <xsl:choose>
                <xsl:when test="$DirNode/parent::wix:Directory/@Id">
                    <xsl:value-of select="$DirNode/parent::wix:Directory/@Id" />
                </xsl:when>
                <xsl:when test="$DirNode/parent::wix:DirectoryRef/@Id">
                    <xsl:value-of select="$DirNode/parent::wix:DirectoryRef/@Id" />
                </xsl:when>
            </xsl:choose>
        </xsl:variable>

        <xsl:if test="$ParentId != ''">
            <xsl:call-template name="GenerateRemoveFolder">
                <xsl:with-param name="DirId" select="$ParentId" />
                <xsl:with-param name="CompId" select="$CompId" />
                <xsl:with-param name="Level" select="$Level + 1" />
            </xsl:call-template>
        </xsl:if>
    </xsl:template>

    <xsl:template match="wix:File/@KeyPath"/>

</xsl:stylesheet>