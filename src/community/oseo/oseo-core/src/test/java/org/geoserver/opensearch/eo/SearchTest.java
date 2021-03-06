/* (c) 2017 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.opensearch.eo;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.*;

import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.geoserver.opensearch.eo.response.AtomSearchResponse;
import org.hamcrest.Matchers;
import org.jsoup.Jsoup;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.xml.SimpleNamespaceContext;
import org.w3c.dom.Document;

public class SearchTest extends OSEOTestSupport {

    @Test
    public void testAllCollection() throws Exception {
        MockHttpServletResponse response = getAsServletResponse(
                "oseo/search?httpAccept=" + AtomSearchResponse.MIME);
        assertEquals(AtomSearchResponse.MIME, response.getContentType());
        assertEquals(200, response.getStatus());

        Document dom = dom(new ByteArrayInputStream(response.getContentAsByteArray()));
        // print(dom);

        // basics
        assertThat(dom, hasXPath("/at:feed/os:totalResults", equalTo("3")));
        assertThat(dom, hasXPath("/at:feed/os:startIndex", equalTo("1")));
        assertThat(dom, hasXPath("/at:feed/os:itemsPerPage", equalTo("10")));
        assertThat(dom, hasXPath("/at:feed/os:Query"));
        assertThat(dom, hasXPath("/at:feed/os:Query[@count='10']"));
        assertThat(dom, hasXPath("/at:feed/os:Query[@startIndex='1']"));
        assertThat(dom, hasXPath("/at:feed/at:author/at:name", equalTo("GeoServer")));
        assertThat(dom, hasXPath("/at:feed/at:updated"));

        assertNoResults(dom);

        // check entries
        assertThat(dom, hasXPath("count(/at:feed/at:entry)", equalTo("3")));
        // ... sorted by date
        assertThat(dom, hasXPath("/at:feed/at:entry[1]/at:title", equalTo("SENTINEL2")));
        assertThat(dom, hasXPath("/at:feed/at:entry[2]/at:title", equalTo("SENTINEL1")));
        assertThat(dom, hasXPath("/at:feed/at:entry[3]/at:title", equalTo("LANDSAT8")));

        // check the sentinel2 one
        assertThat(dom, hasXPath("/at:feed/at:entry[1]/at:id", equalTo(
                "http://localhost:8080/geoserver/oseo/search?uid=SENTINEL2&httpAccept=application%2Fatom%2Bxml")));
        assertThat(dom,
                hasXPath("/at:feed/at:entry[1]/at:updated", equalTo("2016-02-26T09:20:21Z")));
        // ... mind the lat/lon order
        assertThat(dom,
                hasXPath(
                        "/at:feed/at:entry[1]/georss:where/gml:Polygon/gml:exterior/gml:LinearRing/gml:posList",
                        equalTo("89.0 -179.0 89.0 179.0 -89.0 179.0 -89.0 -179.0 89.0 -179.0")));
        // ... the links (self, metadata)
        assertThat(dom, hasXPath(
                "/at:feed/at:entry[1]/at:link[@rel='self' and  @type='application/atom+xml']/@href",
                equalTo("http://localhost:8080/geoserver/oseo/search?uid=SENTINEL2&httpAccept=application%2Fatom%2Bxml")));
        assertThat(dom, hasXPath(
                "/at:feed/at:entry[1]/at:link[@rel='alternate' and @type='application/vnd.iso.19139+xml']/@href",
                equalTo("http://localhost:8080/geoserver/oseo/metadata?uid=SENTINEL2&httpAccept=application%2Fvnd.iso.19139%2Bxml")));

        // check the html description (right one, and param substitution in links
        XPath xPath = getXPath();
        String summary = xPath.compile("/at:feed/at:entry[1]/at:summary").evaluate(dom);
        assertThat(summary, containsString("Sentinel-2"));
        // parse html using JSoup (DOM not usable, HTML is not valid/well formed XML in general
        org.jsoup.nodes.Document sd = Jsoup.parse(summary);
        String isoHRef = sd.select("a[title=ISO format]").attr("href");
        assertThat(isoHRef, equalTo("http://localhost:8080/geoserver/oseo/metadata?uid=SENTINEL2&httpAccept=application%2Fvnd.iso.19139%2Bxml"));
        String atomHRef = sd.select("a[title=ATOM format]").attr("href");
        assertThat(atomHRef, equalTo("http://localhost:8080/geoserver/oseo/search?uid=SENTINEL2&httpAccept=application%2Fatom%2Bxml"));

        // overall schema validation for good measure
        checkValidAtomFeed(dom);
    }

    protected XPath getXPath() {
        XPath xPath = XPathFactory.newInstance().newXPath();
        xPath.setNamespaceContext(namespaceContext);
        return xPath;
    }

    @Test
    public void testPagingNoResults() throws Exception {
        // first page
        Document dom = getAsDOM("oseo/search?uid=UnknownIdentifier");
        assertNoResults(dom);
    }

    private void assertNoResults(Document dom) {
        assertHasLink(dom, "self", 1, 10);
        assertHasLink(dom, "first", 1, 10);
        assertHasLink(dom, "last", 1, 10);
        assertThat(dom, not(hasXPath("/at:feed/at:link[@rel='previous']")));
        assertThat(dom, not(hasXPath("/at:feed/at:link[@rel='next']")));
    }

    @Test
    public void testPagingFullPages() throws Exception {
        // first page
        Document dom = getAsDOM("oseo/search?count=1");
        assertHasLink(dom, "self", 1, 1);
        assertHasLink(dom, "first", 1, 1);
        assertHasLink(dom, "next", 2, 1);
        assertHasLink(dom, "last", 3, 1);
        assertThat(dom, not(hasXPath("/at:feed/at:link[@rel='previous']")));
        assertThat(dom, hasXPath("count(/at:feed/at:entry)", equalTo("1")));
        assertThat(dom, hasXPath("/at:feed/at:entry[1]/at:title", equalTo("SENTINEL2")));

        // second page
        dom = getAsDOM("oseo/search?count=1&startIndex=2");
        assertHasLink(dom, "self", 2, 1);
        assertHasLink(dom, "first", 1, 1);
        assertHasLink(dom, "previous", 1, 1);
        assertHasLink(dom, "next", 3, 1);
        assertHasLink(dom, "last", 3, 1);
        assertThat(dom, hasXPath("count(/at:feed/at:entry)", equalTo("1")));
        assertThat(dom, hasXPath("/at:feed/at:entry[1]/at:title", equalTo("SENTINEL1")));

        // third and last page
        dom = getAsDOM("oseo/search?count=1&startIndex=3");
        assertHasLink(dom, "self", 3, 1);
        assertHasLink(dom, "first", 1, 1);
        assertHasLink(dom, "previous", 2, 1);
        assertHasLink(dom, "last", 3, 1);
        assertThat(dom, not(hasXPath("/at:feed/at:link[@rel='next']")));
        assertThat(dom, hasXPath("count(/at:feed/at:entry)", equalTo("1")));
        assertThat(dom, hasXPath("/at:feed/at:entry[1]/at:title", equalTo("LANDSAT8")));
    }

    @Test
    public void testPagingPartialPages() throws Exception {
        // first page
        Document dom = getAsDOM("oseo/search?count=2");
        assertHasLink(dom, "self", 1, 2);
        assertHasLink(dom, "first", 1, 2);
        assertHasLink(dom, "next", 3, 2);
        assertHasLink(dom, "last", 3, 2);
        assertThat(dom, not(hasXPath("/at:feed/at:link[@rel='previous']")));
        assertThat(dom, hasXPath("count(/at:feed/at:entry)", equalTo("2")));
        assertThat(dom, hasXPath("/at:feed/at:entry[1]/at:title", equalTo("SENTINEL2")));
        assertThat(dom, hasXPath("/at:feed/at:entry[2]/at:title", equalTo("SENTINEL1")));

        // second page
        dom = getAsDOM("oseo/search?count=2&startIndex=3");
        assertHasLink(dom, "self", 3, 2);
        assertHasLink(dom, "first", 1, 2);
        assertHasLink(dom, "previous", 1, 2);
        assertHasLink(dom, "last", 3, 2);
        assertThat(dom, not(hasXPath("/at:feed/at:link[@rel='next']")));
        assertThat(dom, hasXPath("count(/at:feed/at:entry)", equalTo("1")));
        assertThat(dom, hasXPath("/at:feed/at:entry[1]/at:title", equalTo("LANDSAT8")));
    }

    @Test
    public void testGeoUidCollectionQuery() throws Exception {
        Document dom = getAsDOM("oseo/search?uid=LANDSAT8&httpAccept=" + AtomSearchResponse.MIME);
        print(dom);

        // basics
        assertThat(dom, hasXPath("/at:feed/os:totalResults", equalTo("1")));
        assertThat(dom, hasXPath("/at:feed/os:startIndex", equalTo("1")));
        assertThat(dom, hasXPath("/at:feed/os:itemsPerPage", equalTo("10")));
        assertThat(dom, hasXPath("/at:feed/os:Query"));
        assertThat(dom, hasXPath("/at:feed/os:Query[@count='10']"));
        assertThat(dom, hasXPath("/at:feed/os:Query[@startIndex='1']"));
        assertThat(dom, hasXPath("/at:feed/os:Query[@geo:uid='LANDSAT8']"));

        assertNoResults(dom);

        // check entries
        assertThat(dom, hasXPath("count(/at:feed/at:entry)", equalTo("1")));
        assertThat(dom, hasXPath("/at:feed/at:entry[1]/at:title", equalTo("LANDSAT8")));

        // overall schema validation for good measure
        checkValidAtomFeed(dom);
    }

    private void assertHasLink(Document dom, String rel, int startIndex, int count) {
        assertThat(dom, hasXPath("/at:feed/at:link[@rel='" + rel + "']"));
        assertThat(dom, hasXPath("/at:feed/at:link[@rel='" + rel + "']/@href",
                containsString("startIndex=" + startIndex)));
        assertThat(dom, hasXPath("/at:feed/at:link[@rel='" + rel + "']/@href",
                containsString("count=" + count)));
    }

    @Test
    public void testAllSentinel2Products() throws Exception {
        Document dom = getAsDOM(
                "oseo/search?parentId=SENTINEL2&httpAccept=" + AtomSearchResponse.MIME);
        // print(dom);

        // check that filtering worked
        assertThat(dom, hasXPath("/at:feed/at:entry/at:title", startsWith("S2A")));
        assertThat(dom, not(hasXPath("/at:feed/at:entry[at:title='S1A']")));
        assertThat(dom, not(hasXPath("/at:feed/at:entry[at:title='LS08']")));
    }

    @Test
    public void testSpecificProduct() throws Exception {
        Document dom = getAsDOM(
                "oseo/search?parentId=SENTINEL2&uid=S2A_OPER_MSI_L1C_TL_MTI__20170308T220244_A008933_T11SLT_N02.04&httpAccept="
                        + AtomSearchResponse.MIME);
        // print(dom);

        // check basics
        assertThat(dom, hasXPath("count(/at:feed/at:entry)", equalTo("1")));
        assertThat(dom, hasXPath("/at:feed/at:entry/at:title",
                equalTo("S2A_OPER_MSI_L1C_TL_MTI__20170308T220244_A008933_T11SLT_N02.04")));

        // ... the links (self, metadata)
        assertThat(dom, hasXPath(
                "/at:feed/at:entry/at:link[@rel='self' and  @type='application/atom+xml']/@href",
                equalTo("http://localhost:8080/geoserver/oseo/search?parentId=SENTINEL2&uid=S2A_OPER_MSI_L1C_TL_MTI__20170308T220244_A008933_T11SLT_N02.04&httpAccept=application%2Fatom%2Bxml")));
        assertThat(dom, hasXPath(
                "/at:feed/at:entry/at:link[@rel='alternate' and @type='application/gml+xml']/@href",
                equalTo("http://localhost:8080/geoserver/oseo/metadata?parentId=SENTINEL2&uid=S2A_OPER_MSI_L1C_TL_MTI__20170308T220244_A008933_T11SLT_N02.04&httpAccept=application%2Fgml%2Bxml")));

        // check the HTML
        String summary = getXPath().compile("/at:feed/at:entry[1]/at:summary").evaluate(dom);
        System.out.println(summary);
        // parse html using JSoup (DOM not usable, HTML is not valid/well formed XML in general
        org.jsoup.nodes.Document sd = Jsoup.parse(summary);
        String isoHRef = sd.select("a[title=O&M format]").attr("href");
        assertThat(isoHRef, equalTo("http://localhost:8080/geoserver/oseo/metadata?parentId=SENTINEL2&uid=S2A_OPER_MSI_L1C_TL_MTI__20170308T220244_A008933_T11SLT_N02.04&httpAccept=application%2Fgml%2Bxml"));
        String atomHRef = sd.select("a[title=ATOM format]").attr("href");
        assertThat(atomHRef, equalTo("http://localhost:8080/geoserver/oseo/search?parentId=SENTINEL2&uid=S2A_OPER_MSI_L1C_TL_MTI__20170308T220244_A008933_T11SLT_N02.04&httpAccept=application%2Fatom%2Bxml"));
        String quickLookRef = sd.select("a[title='View browse image'").attr("href");
        assertThat(quickLookRef, equalTo("http://localhost:8080/geoserver/oseo/quicklook?parentId=SENTINEL2&uid=S2A_OPER_MSI_L1C_TL_MTI__20170308T220244_A008933_T11SLT_N02.04"));
    }
    
    @Test
    public void testSearchByBox() throws Exception {
        Document dom = getAsDOM("oseo/search?parentId=SENTINEL2&box=-118,33,-117,34");
        // print(dom);
        
        // only one feature should be matching
        assertThat(dom, hasXPath("count(/at:feed/at:entry)", equalTo("1")));
        assertThat(dom, hasXPath("/at:feed/at:entry/at:title",
                equalTo("S2A_OPER_MSI_L1C_TL_MTI__20170308T220244_A008933_T11SLT_N02.04")));
    }
    
    @Test
    public void testSearchByBoxOutsideData() throws Exception {
        // look for data close to the south pole
        Document dom = getAsDOM("oseo/search?parentId=SENTINEL2&box=0,-89,1,-88");
        // print(dom);
        
        assertNoResults(dom);
    }
    
    @Test
    public void testSearchByDistance() throws Exception {
        // test distance search, this distance is just good enough
        Document dom = getAsDOM("oseo/search?parentId=SENTINEL2&lon=-117&lat=33&radius=150000");

        // only one feature should be matching
        assertThat(dom, hasXPath("count(/at:feed/at:entry)", equalTo("1")));
        assertThat(dom, hasXPath("/at:feed/at:entry/at:title",
                equalTo("S2A_OPER_MSI_L1C_TL_MTI__20170308T220244_A008933_T11SLT_N02.04")));
    }
    
    @Test
    public void testSearchByDistanceWhereNoDataIsAvailable() throws Exception {
        // since H2 does not support well distance searches, use a point inside the data area
        Document dom = getAsDOM("oseo/search?parentId=SENTINEL2&lon=0&lat=-89&radius=10000");

        assertNoResults(dom);
    }

    @Test
    public void testGetSentinel2Metadata() throws Exception {
        Document dom = getAsDOM("oseo/metadata?uid=SENTINEL2", 200, MetadataRequest.ISO_METADATA);
        // print(dom);

        // just check we got the right one
        assertThat(dom, hasXPath("/gmi:MI_Metadata/gmd:fileIdentifier/gco:CharacterString",
                equalTo("EOP:CNES:PEPS:S2")));
    }

    @Test
    public void testGetSentinel1Metadata() throws Exception {
        Document dom = getAsDOM("oseo/metadata?uid=SENTINEL1", 200, MetadataRequest.ISO_METADATA);
        // print(dom);

        // just check we got the right one
        assertThat(dom, hasXPath("/gmi:MI_Metadata/gmd:fileIdentifier/gco:CharacterString",
                equalTo("EOP:CNES:PEPS:S1")));
    }

    @Test
    public void testProductMetadata() throws Exception {
        String path = "oseo/metadata?parentId=SENTINEL2&uid=S2A_OPER_MSI_L1C_TL_SGS__20160929T154211_A006640_T32TPP_N02.04&httpAccept=application/gml%2Bxml";
        Document dom = getAsDOM(path, 200, MetadataRequest.OM_METADATA);
        // print(dom);

        // just check we got the right one (the namespaces used here are different than the ones
        // used
        SimpleNamespaceContext ctx = new SimpleNamespaceContext();
        ctx.bindNamespaceUri("gml", "http://www.opengis.net/gml/3.2");
        ctx.bindNamespaceUri("opt", "http://www.opengis.net/opt/2.1");
        ctx.bindNamespaceUri("om", "http://www.opengis.net/om/2.0");
        assertThat(dom,
                Matchers.hasXPath(
                        "/opt:EarthObservation/om:phenomenonTime/gml:TimePeriod/gml:beginPosition",
                        ctx, equalTo("2016-09-29T10:20:22.026Z")));
    }

    @Test
    public void testGetCollectionMetadataInvalidFormat() throws Exception {
        Document dom = getAsOpenSearchException("oseo/metadata?uid=SENTINEL2&httpAccept=foo/bar",
                400);
        assertThat(dom,
                hasXPath("/rss/channel/item/title", containsString(MetadataRequest.ISO_METADATA)));
    }

    @Test
    public void testGetProductMetadataInvalidFormat() throws Exception {
        Document dom = getAsOpenSearchException(
                "oseo/metadata?parentId=SENTINEL2&uid=123&httpAccept=foo/bar", 400);
        assertThat(dom,
                hasXPath("/rss/channel/item/title", containsString(MetadataRequest.OM_METADATA)));
    }
    
    @Test
    public void testQuicklook() throws Exception {
        String path = "oseo/quicklook?parentId=SENTINEL2&uid=S2A_OPER_MSI_L1C_TL_SGS__20160117T141030_A002979_T33TWH_N02.01";
        RenderedImage image = getAsImage(path, "image/jpeg");
        assertNotNull(image);
    }

}
