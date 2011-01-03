(ns midje.t-semi-sweet
  (:use clojure.test)
  (:use [midje.sweet]
	[midje.util form-utils])
  (:use [midje.test-util]))
(testable-privates midje.semi-sweet fakes-and-overrides)

(unfinished faked-function mocked-function other-function)

(fact "separating overrides of an #expect from fakes"
  ;; The lets are because fact isn't smart enough not to add overrides to fake call otherwise.
  (let [actual (fakes-and-overrides '( (fake (f 1) => 2) :key 'value))]
    actual => [  '[(fake (f 1) => 2)]
		 '[:key 'value] ])

  (let [actual (fakes-and-overrides '( (not-called some-function) :key 'value))]
    actual => [ '[(not-called some-function)]
		'[:key 'value] ])

  ;; often passed a seq.
  (let [actual (fakes-and-overrides (seq '( (fake (f 1) => 2) :key 'value)))]
    actual => [  '[(fake (f 1) => 2)]
		 '[:key 'value] ])

  (let [actual (fakes-and-overrides '())]
    actual => (just [empty? empty?])))

(fact "calling a faked function raises an error"
  (faked-function) => (throws Error))

(facts "about the creation of fake maps"
  (let [some-variable 5
	previous-line-position (file-position 1)
	fake-0 (fake (faked-function) => 2)
	fake-1 (fake (faked-function some-variable) => (+ 2 some-variable))
	fake-2 (fake (faked-function 1 some-variable) => [1 some-variable])]

    "The basic parts"
    (:function fake-0) => #'midje.t-semi-sweet/faked-function
    (:call-text-for-failures fake-1) => "(faked-function some-variable)"
    (deref (:count-atom fake-0)) => 0

    "argument matching"
    (count (:arg-matchers fake-0)) => 0

    "Note that lexical scoping is obeyed"
    (count (:arg-matchers fake-1)) => 1
    (apply-pairwise (:arg-matchers fake-1) [5] [nil]) => [[true] [false]]
    (count (:arg-matchers fake-2)) => 2
    (apply-pairwise (:arg-matchers fake-2) [5 5] [1 1]) => [  [false true]
							      [true false] ]

    "Result supplied"
    ((:result-supplier fake-0)) => 2
    ((:result-supplier fake-1)) => (+ 2 some-variable)
    ((:result-supplier fake-2)) => [1 some-variable]
))

(deftest fakes-with-overrides-test
  (let [fake (fake (faked-function) => 2 :file-position 33)]
    (is (= 33 (fake :file-position))))

  (let [filepos 33
	fake (fake (faked-function) => 2 :file-position filepos)]
    (is (= 33 (fake :file-position))))
  )

(deftest basic-not-called-test
  (let [fake-0 (not-called faked-function)]

    (is (= (:function fake-0)
           #'midje.t-semi-sweet/faked-function))
    (is (= (:call-text-for-failures fake-0)
           "faked-function was called."))
    (is (= (deref (:count-atom fake-0))
           0))

    (testing "arg-matchers are not needed" 
      (let [matchers (:arg-matchers fake-0)]
        (is (nil? matchers)))
    )

    (testing "result supplied" 
	     (is (= ((:result-supplier fake-0))
		    nil)))
    )
 )


(defn function-under-test [& rest]
  (apply mocked-function rest))
(defn no-caller [])

(deftest simple-examples
  (one-case "Without fakes, this is like 'is'."
    (expect (+ 1 3) => nil)
    (is (reported? 1 [{:type :mock-expected-result-failure
		      :actual 4
		      :expected nil}])))

  (one-case "A passing test so reports"
    (expect (+ 1 3) => 4)
    (is (reported? 1 [{:type :pass}])))
	    

  (one-case "successful mocking"
    (expect (function-under-test) => 33
       (fake (mocked-function) => 33))
    (is (no-failures?)))

  (one-case "successful mocking with not-called expected first"
    (expect (function-under-test) => 33
       (not-called no-caller)
       (fake (mocked-function) => 33))
    (is (no-failures?)))

  (one-case "successful mocking not-called expected last"
    (expect (function-under-test) => 33
       (fake (mocked-function) => 33)
       (not-called no-caller))
    (is (no-failures?)))


  (one-case "mocked calls go fine, but function under test produces the wrong result"
     (expect (function-under-test 33) => 12
	(fake (mocked-function 33) => (not 12) ))
     (is (reported? 1 [{:actual false
			:expected 12}])))

  (one-case "mock call supposed to be made, but wasn't (zero call count)"
    (expect (no-caller) => "irrelevant"
	    (fake (mocked-function) => 33))
    (is (reported? 2 [{:type :mock-incorrect-call-count}
		      {:type :mock-expected-result-failure}])))

  (one-case "mock call was not supposed to be made, but was (non-zero call count)"
     (expect (function-under-test 33) => "irrelevant"
             (not-called mocked-function))
    (is (reported? 2 [{:type :mock-incorrect-call-count}
		      {:type :mock-expected-result-failure}])))

  (one-case "call not from inside function"
     (expect (+ (mocked-function 12) (other-function 12)) => 12
	     (fake (mocked-function 12) => 11)
	     (fake (other-function 12) => 1))
     (is (no-failures?)))



  (one-case "call that matches none of the expected arguments"
     (expect (+ (mocked-function 12) (mocked-function 33)) => "result irrelevant because of earlier failure"
	     (fake (mocked-function 12) => "hi"))
    (is (reported? 2 [{:type :mock-argument-match-failure
		       :actual '(33)}
		      {:type :mock-expected-result-failure}])))

  (one-case "failure because one variant of multiply-mocked function is not called"
     (expect (+ (mocked-function 12) (mocked-function 22)) => 3
	     (fake (mocked-function 12) => 1)
	     (fake (mocked-function 22) => 2)
	     (fake (mocked-function 33) => 3))
    (is (reported? 2 [{:type :mock-incorrect-call-count
		       :expected-call "(mocked-function 33)" }
		      {:type :pass}]))) ; passes for wrong reason

  (one-case "multiple calls to a mocked function are perfectly fine"
     (expect (+ (mocked-function 12) (mocked-function 12)) => 2
	      (fake (mocked-function 12) => 1) ))
)

(deftest expect-with-overrides-test
  (one-case "can override entries in call-being-tested map" 
     (expect (function-under-test 1) => 33 :expected-result "not 33"
	       (fake (mocked-function 1) => "not 33"))
     (is (no-failures?)))

  (let [expected "not 33"]
    (expect (function-under-test 1) => 33 :expected-result expected
	    (fake (mocked-function 1) => "not 33"))))

(deftest duplicate-overrides---last-one-takes-precedence
  (let [expected "not 33"]
    (expect (function-under-test 1) => 33 :expected-result "to be overridden"
	                                  :expected-result expected
	  (fake (mocked-function 1) => "not 33"))
    (expect (function-under-test 1) => 33 :expected-result "to be overridden"
	                                  :expected-result expected
	  (fake (mocked-function 1) => 5 :result-supplier "IGNORED"
		                         :result-supplier (fn [] expected)))))
    

(deftest expect-returns-truth-value-test
  (is (true? (run-silently (expect (function-under-test 1) => 33
				   (fake (mocked-function 1) => 33)))))   
  (is (false? (run-silently (expect (function-under-test 1) => 33
				   (fake (mocked-function 2) => 33)))))  ; mock failure
  (is (false? (run-silently (expect (+ 1 1) => 33))))
)  

  
(deftest function-awareness-test
  (one-case "expected results can be functions"
     (expect (+ 1 1) => even?)
     (is (no-failures?)))
  (let [myfun (fn [] 33)
	funs [myfun]]
    (one-case "exact function matches can be checked with exactly"
       (expect (first funs) => (exactly myfun))
       (is (no-failures?))))

  (one-case "mocked function argument matching uses function-aware equality"
     (expect (function-under-test 1 "floob" even?) => even?
	     (fake (mocked-function odd? anything (exactly even?)) => 44))
     (is (no-failures?)))
  )



(defn actual-plus-one-is-greater-than [expected]
  (chatty-checker [actual] (> (inc actual) expected)))

(deftest chatty-function-awareness-test
  (one-case "chatty failures provide extra information"
     (expect (+ 1 1) => (actual-plus-one-is-greater-than 33))
     (is (reported? 1 [ {:type :mock-expected-result-functional-failure
			 :actual 2
			 :intermediate-results [ [ '(inc actual) 3 ] ]
			 :expected '(actual-plus-one-is-greater-than 33)} ])))
  (one-case "chatty checkers can be used inline"
     (expect (+ 1 1) => (chatty-checker [actual] (> (inc actual) 33)))
     (is (reported? 1 [ {:type :mock-expected-result-functional-failure
			 :actual 2
			 :intermediate-results [ [ '(inc actual) 3 ] ]
			 :expected '(chatty-checker [actual] (> (inc actual) 33))} ]))))

(declare chatty-prerequisite)
(defn chatty-fut [x] (chatty-prerequisite x))
(deftest chatty-functions-can-be-used-in-fake-args
  (one-case "chatty checkers can be used in fakes"
	    (expect (chatty-fut 5) => "hello"
		    (fake (chatty-prerequisite (actual-plus-one-is-greater-than 5)) => "hello"))
	    (is (no-failures?))))


(deftest fake-function-from-other-ns
  (let [myfun (fn [x] (list x))]
    (expect (myfun 1) => :list-called
            (fake (list 1) => :list-called))))

(use 'clojure.set)
(defn set-handler [set1 set2]
  (if (empty? (intersection set1 set2))
    set1
    (intersection set1 set2)))

(deftest fake-function-from-other-namespace-used-in-var
  (expect (set-handler 'set 'disjoint-set) => 'set
	  (fake (intersection 'set 'disjoint-set) => #{}))
  (expect (set-handler 'set 'overlapping-set) => #{'intersection}
	  (fake (intersection 'set 'overlapping-set) => #{'intersection}))
)


;; This test is rather indirect. The function under test returns a lazy seq
;; embedded within a top-level list. If the whole tree isn't evaluated, the
;; test will fail because the fake is never called. (Because fake results are
;; checked before final results, since that results in nicer output.)

(def testfun)
(defn lazy-seq-not-at-top-level []
  (list (map (fn [n] (testfun n)) [1])))

(deftest entire-trees-are-eagerly-evaluated
  (expect (lazy-seq-not-at-top-level) => '((32))
	  (fake (testfun 1) => 32)))

(binding [clojure.test/*load-tests* false]
  (load "semi_sweet_compile_out"))
  
(binding [midje.semi-sweet/*include-midje-checks* false]
  (load "semi_sweet_compile_out"))
