# Examples 2/2 #

## Parallelism ##

<pre class="sourceCode java"><code class="sourceCode java">    <span class="dt">int</span> sumOfWeight 
        = shapes.<span class="fu">parallelStream</span>()
                .<span class="fu">filter</span>(s -&gt; s.<span class="fu">getColor</span>() == BLUE)
                .<span class="fu">map</span>(Shape::getWeight)
                .<span class="fu">sum</span>();</code></pre>

