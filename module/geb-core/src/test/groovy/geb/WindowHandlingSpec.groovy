package geb

import geb.test.util.GebSpecWithServer
import geb.driver.CachingDriverFactory
import groovy.xml.MarkupBuilder
import spock.lang.Unroll
import org.openqa.selenium.NoSuchWindowException

class WindowHandlingSpec extends GebSpecWithServer {

	private final static String MAIN_PAGE_URL = '/main'

	def cleanup() {
		/*
		 * set the browser instance to null as we're going to quit the driver and otherwise parent's cleanup will
		 * want to do some work on that closed driver which will fail
		 */
		resetBrowser()
		// make sure that dirver is recreated for the next test so that there is only one browser window opened
		CachingDriverFactory.clearCacheAndQuitDriver()
	}

	def setupSpec() {
		server.get = { req, res ->
			def writer = new OutputStreamWriter(res.outputStream)
			def page = (~'/(.*)').matcher(req.requestURI)[0][1]
			new MarkupBuilder(writer).html {
				head {
					title("Window $page")
				}
				body {
					[1, 2].each {
						def label = "$page-$it"
						a(target: "window-$label", href: "/$label")
					}
				}
			}
		}
	}

	private void allWindowsOpened() {
		go MAIN_PAGE_URL
		$('a')*.click()
		assert availableWindows.size() == 3
	}

	private boolean isInContextOfMainWindow() {
		$('title').text() == 'Window main'
	}

	private String windowTitle(int[] indexes = []) {
		def name = "Window main"
		if (indexes) {
			name += "-" + indexes*.toString().join("-")
		}
		name
	}

	private String windowName(int[] indexes = []) {
		def name = "window-main"
		if (indexes) {
			name += "-" + indexes*.toString().join("-")
		}
		name
	}

	@Unroll
	def "withWindow changes focus to window with given name and returns closure return value"() {
		when:
		allWindowsOpened()

		then:
		withWindow(windowName(index)) { title } == windowTitle(index)

		where:
		index << [1,2]
	}

	@Unroll
	def "ensure original context is preserved after a call to withWindow"() {
		given:
		allWindowsOpened()

		when:
		withWindow(specification) {}

		then:
		inContextOfMainWindow

		when:
		withWindow(specification) { throw new Exception() }

		then:
		thrown(Exception)
		inContextOfMainWindow

		where:
		specification << [windowName(1), { $('title').text() == windowTitle(1) }]
	}

	@Unroll
	def "ensure exception is thrown for a non existing window passed to withWindow"() {
		when:
		withWindow(specification) {}

		then:
		thrown(NoSuchWindowException)

		where:
		specification << ['nonexisting', { false }]
	}

	@Unroll
	def "ensure withWindow block closure parameter called for all windows for which specification closure returns true"() {
		given:
		allWindowsOpened()

		when:
		def called = 0
		withWindow(specification) { called++ }

		then:
		called == expecetedCalls

		where:
		expecetedCalls | specification
		3              | { true }
		1              | { $('title').text() == windowTitle() }
		2              | { $('title').text() in [windowTitle(1), windowTitle(2)] }
	}

	@Unroll("ensure withNewWindow throws an exception when: '#message'")
	def "ensure withNewWindow throws exception if there was none or more than one windows opened"() {
		when:
		withNewWindow(newWindowBlock) {}

		then:
		NoSuchWindowException e = thrown()
		e.message.startsWith(message)

		where:
		message                                      | newWindowBlock
		'No new window has been opened'              | {}
		'There has been more than one window opened' | { allWindowsOpened() }
	}

	def "ensure original context is preserved after a call to withNewWindow"() {
		given:
		go MAIN_PAGE_URL

		when:
		withNewWindow({ $('a', 0).click() }) {}

		then:
		inContextOfMainWindow

		when:
		withNewWindow({ $('a', 1).click() }) { throw new Exception() }

		then:
		thrown(Exception)
		inContextOfMainWindow
	}

	@Unroll
	def "ensure withNewWindow block closure called in the context of the newly opened window"() {
		when:
		go MAIN_PAGE_URL

		then:
		withNewWindow({ $('a', anchorIndex).click() }) { title } == expectedTitle

		where:
		expectedTitle  | anchorIndex
		windowTitle(1) | 0
		windowTitle(2) | 1
	}
}