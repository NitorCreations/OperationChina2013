<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="http://www.w3.org/1999/xhtml" xmlns:h="http://www.w3.org/1999/xhtml" version="1.0">
  <xsl:output method="text"/>
  <xsl:template match="/">
    <output>
        <xsl:for-each select="//h:video">
           <xsl:value-of select="h:source/@src"/><xsl:text>&#10;</xsl:text>
           <xsl:value-of select="h:img/@src"/><xsl:text>&#10;</xsl:text>
        </xsl:for-each>
    </output>
  </xsl:template>
</xsl:stylesheet>
