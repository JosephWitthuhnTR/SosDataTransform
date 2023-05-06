Questions? For help with this application, contact Joseph Witthuhn (jwitthuhn@uwalumni.com).


HOW TO RUN
==================================

First, put your input files from the state in the "input" folder (create it if it does not exist). The, configure the
program as described in the sections below. Once you are ready, run SosDataTransform.bat.

Once run, an "output" folder will be created for the resulting Excel files.

To run this, you will need Java 17 or later installed. If you do not already have this, you can download Amazon Corretto
here: https://aws.amazon.com/corretto/

(If you wish to simply run it, a JRE is sufficient. If you wish to make changes to the code and recompile it, a JDK is
needed.)


WHICH ELECTIONS DO YOU CARE ABOUT?
==================================

In the config folder, open elections.txt. Put one election on each line. For each line, put the election date in the
format MM/DD/YYYY (include leading zeros in months and days), and then a comma, and then the column header for that
election. Elections will be added to the output file in the same order as you list them in the file.

For example, your file might look like this (you can add/remove/reorder/retitle elections as you wish):

```
08/14/2018,Primary2018
11/06/2018,General2018
11/05/2019,Local2019
03/03/2020,PresidentialPrimary2020
08/11/2020,Primary2020
11/03/2020,General2020
```

FILTER BY DISTRICT
==================================

You can set it to only process certain Senate Districts or School Districts by opening these files in the config folder.
The file should be empty except for a list of Senate or School District numbers, one per line. DO NOT INCLUDE LEADING
ZEROES.

```
senateDistricts.txt
schoolDistricts.txt
```

If the files are empty or missing, no filter will be applied.


ADDITIONAL ELECTIONS
==================================

For reference, here are all of the primary and general elections from 2012-2022.
(this does not include special elections).

```
08/14/2012,P2012
11/06/2012,G2012
11/05/2013,G2013
08/12/2014,P2014
11/04/2014,G2014
11/03/2015,G2015
08/09/2016,P2016
11/08/2016,G2016
11/07/2017,G2017
08/14/2018,P2018
11/06/2018,G2018
11/05/2019,G2019
03/03/2020,PP2020
08/11/2020,P2020
11/03/2020,G2020
11/02/2021,G2021
08/09/2022,P2022
11/08/2022,G2022
```
